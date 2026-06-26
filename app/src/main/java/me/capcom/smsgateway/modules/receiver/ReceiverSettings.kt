package me.capcom.smsgateway.modules.receiver

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class ReceiverSettings(
    private val storage: KeyValueStorage,
) {
    var contentProviderEnabled: Boolean
        get() = storage.get<Boolean>(CONTENT_PROVIDER_ENABLED) ?: true
        set(value) = storage.set(CONTENT_PROVIDER_ENABLED, value)

    companion object {
        private const val CONTENT_PROVIDER_ENABLED = "content_provider_enabled"
    }
}
