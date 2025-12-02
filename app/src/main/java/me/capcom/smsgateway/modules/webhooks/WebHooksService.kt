package me.capcom.smsgateway.modules.webhooks

import android.content.Context
import android.webkit.URLUtil
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.R
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.helpers.BuildHelper
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.notifications.NotificationsService
import me.capcom.smsgateway.modules.webhooks.db.WebHook
import me.capcom.smsgateway.modules.webhooks.db.WebHooksDao
import me.capcom.smsgateway.modules.webhooks.domain.WebHookDTO
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEventDTO
import me.capcom.smsgateway.modules.webhooks.workers.ReviewWebhooksWorker
import me.capcom.smsgateway.modules.webhooks.workers.SendWebhookWorker
import me.capcom.smsgateway.modules.webhooks.workers.WebhookQueueProcessorWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.net.URL

class WebHooksService(
    private val webHooksDao: WebHooksDao,
    private val localServerSettings: LocalServerSettings,
    private val gatewaySettings: GatewaySettings,
    private val webhooksSettings: WebhooksSettings,
    private val notificationsService: NotificationsService,
    private val logsService: LogsService,
) : KoinComponent {
    private val eventsReceiver by lazy { EventsReceiver() }

    fun start(context: Context) {
        eventsReceiver.start()
        ReviewWebhooksWorker.start(context)
        WebhookQueueProcessorWorker.start(context, webhooksSettings.internetRequired)
    }

    fun stop(context: Context) {
        WebhookQueueProcessorWorker.stop(context)
        ReviewWebhooksWorker.stop(context)
        eventsReceiver.stop()
    }

    fun select(source: EntitySource?): List<WebHookDTO> {
        return when (source) {
            null -> webHooksDao.select()
            else -> webHooksDao.selectBySource(source)
        }.map {
            WebHookDTO(
                id = it.id,
                deviceId = null,
                url = it.url,
                event = it.event,
                source = it.source,
            )
        }
    }

    fun sync(source: EntitySource, webHooks: List<WebHookDTO>) {
        val ids = webHooksDao.selectBySource(source).map { it.id }.toSet()
        if (webHooks.any { it.id !in ids && it.event == WebHookEvent.SmsReceived }) {
            notifyUser()
        }

        webHooksDao.replaceAll(source, webHooks.map {
            WebHook(
                id = requireNotNull(it.id) { "ID is required for sync" },
                url = it.url,
                event = it.event,
                source = source,
            )
        })
    }

    fun replace(source: EntitySource, webHook: WebHookDTO): WebHookDTO {
        val isHttps = URLUtil.isHttpsUrl(webHook.url)
        val isHttp = URLUtil.isHttpUrl(webHook.url)
        val isLocalhost = isHttp && URL(webHook.url).host == "127.0.0.1"

        val isValidUrl = isHttps ||
                (BuildHelper.isInsecureVersion && isHttp) ||
                isLocalhost

        if (!isValidUrl) {
            throw IllegalArgumentException("url must start with https:// or http://127.0.0.1")
        }
        
        if (webHook.event !in WebHookEvent.values()) {
            throw IllegalArgumentException(
                "Unsupported event"
            )
        }

        val webHookEntity = WebHook(
            id = webHook.id ?: NanoIdUtils.randomNanoId(),
            url = webHook.url,
            event = webHook.event,
            source = source,
        )

        val exists = webHooksDao.exists(source, webHookEntity.id)
        webHooksDao.upsert(webHookEntity)

        // Show notification if this is an sms:received webhook
        if (!exists && webHook.event == WebHookEvent.SmsReceived) {
            notifyUser()
        }

        return WebHookDTO(
            id = webHookEntity.id,
            deviceId = null,
            url = webHookEntity.url,
            event = webHookEntity.event,
            source = webHookEntity.source,
        )
    }

    fun delete(source: EntitySource, id: String) {
        webHooksDao.delete(source, id)
    }

    fun emit(context: Context, event: WebHookEvent, payload: Any) {
        val webhooksToProcess = webHooksDao.selectByEvent(event)
        var queuedCount = 0
        var skippedCount = 0

        webhooksToProcess.forEach { webhook ->
            // skip emitting if source is disabled
            when {
                webhook.source == EntitySource.Local && !localServerSettings.enabled -> {
                    skippedCount++
                    return@forEach
                }

                (webhook.source == EntitySource.Cloud || webhook.source == EntitySource.Gateway) && !gatewaySettings.enabled -> {
                    skippedCount++
                    return@forEach
                }
            }

            val deviceId = when (webhook.source) {
                EntitySource.Local -> localServerSettings.deviceId
                EntitySource.Cloud, EntitySource.Gateway -> gatewaySettings.deviceId
            } ?: run {
                skippedCount++
                return@forEach
            }

            try {
                // Create the webhook event DTO
                val webhookEventDTO = WebHookEventDTO(
                    id = NanoIdUtils.randomNanoId(),
                    webhookId = webhook.id,
                    event = event,
                    deviceId = deviceId,
                    payload = payload,
                )

                SendWebhookWorker.start(
                    context = context,
                    url = webhook.url,
                    data = webhookEventDTO,
                    internetRequired = webhooksSettings.internetRequired
                )

                queuedCount++

                logsService.insert(
                    LogEntry.Priority.DEBUG,
                    NAME,
                    "Queued webhook event for processing",
                    mapOf(
                        "webhookId" to webhook.id,
                        "event" to event.name,
                        "internetRequired" to webhooksSettings.internetRequired
                    )
                )
            } catch (e: Exception) {
                logsService.insert(
                    LogEntry.Priority.ERROR,
                    NAME,
                    "Failed to queue webhook event",
                    mapOf(
                        "webhookId" to webhook.id,
                        "event" to event.name,
                        "error" to e.message
                    )
                )
                skippedCount++
            }
        }

        // Log summary for debugging
        if (webhooksToProcess.isNotEmpty()) {
            logsService.insert(
                LogEntry.Priority.DEBUG,
                NAME,
                "Webhook emission summary",
                mapOf(
                    "event" to event.name,
                    "totalWebhooks" to webhooksToProcess.size,
                    "queued" to queuedCount,
                    "skipped" to skippedCount
                )
            )
        }
    }

    private fun notifyUser() {
        val context = get<Context>()
        notificationsService.notify(
            context,
            NotificationsService.NOTIFICATION_ID_SMS_RECEIVED_WEBHOOK,
            context.getString(R.string.new_sms_received_webhooks_registered)
        )
    }
}