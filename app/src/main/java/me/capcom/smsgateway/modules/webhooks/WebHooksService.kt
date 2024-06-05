package me.capcom.smsgateway.modules.webhooks

import android.webkit.URLUtil
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.webhooks.db.WebHook
import me.capcom.smsgateway.modules.webhooks.db.WebHooksDao
import me.capcom.smsgateway.modules.webhooks.domain.WebHookDTO

class WebHooksService(
    private val webHooksDao: WebHooksDao
) {
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
        if (!URLUtil.isHttpUrl(webHook.url) && !URLUtil.isHttpsUrl(webHook.url)) {
            throw IllegalArgumentException("Invalid URL")
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
}