package me.capcom.smsgateway.modules.webhooks

import android.webkit.URLUtil
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.webhooks.db.WebHook
import me.capcom.smsgateway.modules.webhooks.db.WebHooksDao
import me.capcom.smsgateway.modules.webhooks.domain.WebHookDTO
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import me.capcom.smsgateway.modules.webhooks.workers.SendWebhookWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class WebHooksService(
    private val webHooksDao: WebHooksDao
) : KoinComponent {
    fun select(source: EntitySource): List<WebHookDTO> {
        return webHooksDao.selectBySource(source).map {
            WebHookDTO(
                id = it.id,
                url = it.url,
                event = it.event,
            )
        }
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

        webHooksDao.replace(
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
            SendWebhookWorker.start(get(), event = event, url = it.url, payload = payload)
        }
    }

}