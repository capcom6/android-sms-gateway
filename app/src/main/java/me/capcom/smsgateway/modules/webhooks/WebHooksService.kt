package me.capcom.smsgateway.modules.webhooks

import android.content.Context
import android.webkit.URLUtil
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.R
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.notifications.NotificationsService
import me.capcom.smsgateway.modules.webhooks.db.WebHook
import me.capcom.smsgateway.modules.webhooks.db.WebHooksDao
import me.capcom.smsgateway.modules.webhooks.domain.WebHookDTO
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEventDTO
import me.capcom.smsgateway.modules.webhooks.workers.SendWebhookWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.net.URL

class WebHooksService(
    private val webHooksDao: WebHooksDao,
    private val localServerSettings: LocalServerSettings,
    private val gatewaySettings: GatewaySettings,
    private val webhooksSettings: WebhooksSettings,
    private val notificationsService: NotificationsService,
) : KoinComponent {
    private val eventsReceiver by lazy { EventsReceiver() }

    fun start(context: Context) {
        eventsReceiver.start()
    }

    fun stop(context: Context) {
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
        if (!URLUtil.isHttpsUrl(webHook.url)
            && !(URLUtil.isHttpUrl(webHook.url) && URL(webHook.url).host == "127.0.0.1")
        ) {
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

    fun emit(event: WebHookEvent, payload: Any) {
        webHooksDao.selectByEvent(event).forEach {
            // skip emitting if source is disabled
            when {
                it.source == EntitySource.Local && !localServerSettings.enabled -> return@forEach
                (it.source == EntitySource.Cloud || it.source == EntitySource.Gateway) && !gatewaySettings.enabled -> return@forEach
            }

            val deviceId = when (it.source) {
                EntitySource.Local -> localServerSettings.deviceId
                EntitySource.Cloud, EntitySource.Gateway -> gatewaySettings.deviceId
            } ?: return@forEach

            SendWebhookWorker.start(
                get(),
                url = it.url,
                WebHookEventDTO(
                    id = NanoIdUtils.randomNanoId(),
                    webhookId = it.id,
                    event = event,
                    deviceId = deviceId,
                    payload = payload,
                ),
                webhooksSettings.internetRequired
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