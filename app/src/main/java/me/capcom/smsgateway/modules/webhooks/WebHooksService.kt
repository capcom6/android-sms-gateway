package me.capcom.smsgateway.modules.webhooks

import android.content.Context
import android.webkit.URLUtil
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.webhooks.db.WebHook
import me.capcom.smsgateway.modules.webhooks.db.WebHooksDao
import me.capcom.smsgateway.modules.webhooks.domain.WebHookDTO
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEventDTO
import me.capcom.smsgateway.modules.webhooks.workers.SendWebhookWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class WebHooksService(
    private val webHooksDao: WebHooksDao,
    private val localServerSettings: LocalServerSettings,
    private val gatewaySettings: GatewaySettings,
    private val webhooksSettings: WebhooksSettings,
) : KoinComponent {
    private val eventsReceiver by lazy { EventsReceiver() }

    fun start(context: Context) {
        eventsReceiver.start()
    }

    fun stop(context: Context) {
        eventsReceiver.stop()
    }

    fun select(source: EntitySource): List<WebHookDTO> {
        return webHooksDao.selectBySource(source).map {
            WebHookDTO(
                id = it.id,
                url = it.url,
                event = it.event,
            )
        }
    }

    fun sync(source: EntitySource, webHooks: List<WebHookDTO>) {
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
        if (!URLUtil.isHttpsUrl(webHook.url)) {
            throw IllegalArgumentException("Invalid URL")
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
        webHooksDao.upsert(webHookEntity)

        return WebHookDTO(
            id = webHookEntity.id,
            url = webHookEntity.url,
            event = webHookEntity.event,
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

}