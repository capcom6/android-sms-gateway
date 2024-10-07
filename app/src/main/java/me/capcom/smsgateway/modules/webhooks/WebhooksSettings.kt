package me.capcom.smsgateway.modules.webhooks

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class WebhooksSettings(
    private val storage: KeyValueStorage,
) {
    val internetRequired: Boolean
        get() = storage.get<Boolean>(INTERNET_REQUIRED) ?: true

    companion object {
        const val INTERNET_REQUIRED = "internet_required"
    }
}