package me.capcom.smsgateway.modules.webhooks

import android.webkit.URLUtil
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.localserver.LocalServerService
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
    localserverSvc: LocalServerService,
    gatewayService: GatewayService,
) : KoinComponent {
    val localDeviceId: String?
    val cloudDeviceId: String?

    init {
        localDeviceId = localserverSvc.getDeviceId(get())
        cloudDeviceId = gatewayService.getDeviceId(get())
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

    fun replace(source: EntitySource, webHook: WebHookDTO) {
        if (!URLUtil.isHttpsUrl(webHook.url)) {
            throw IllegalArgumentException("Invalid URL")
        }
        if (webHook.event !in WebHookEvent.values()) {
            throw IllegalArgumentException(
                "Unsupported event"
            )
        }

        webHooksDao.upsert(
            WebHook(
                id = webHook.id ?: NanoIdUtils.randomNanoId(),
                url = webHook.url,
                event = webHook.event,
                source = source,
            )
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
                EntitySource.Local -> localDeviceId
                EntitySource.Cloud, EntitySource.Gateway -> cloudDeviceId
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
                )
            )
        }
    }

}