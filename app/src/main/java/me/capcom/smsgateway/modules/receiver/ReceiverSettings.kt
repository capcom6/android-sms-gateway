package me.capcom.smsgateway.modules.receiver

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class ReceiverSettings(
    private val storage: KeyValueStorage,
) {
    var contentProviderEnabled: Boolean
        get() = storage.get<Boolean>(CONTENT_PROVIDER_ENABLED) ?: true
        set(value) = storage.set(CONTENT_PROVIDER_ENABLED, value)

    /**
     * Report outgoing SMS composed on the device itself via the
     * `sms:device-sent` webhook. Opt-in: disabled by default so existing
     * installations keep their current behaviour and webhook volume.
     */
    var deviceSentEnabled: Boolean
        get() = storage.get<Boolean>(DEVICE_SENT_ENABLED) ?: false
        set(value) = storage.set(DEVICE_SENT_ENABLED, value)

    companion object {
        private const val CONTENT_PROVIDER_ENABLED = "content_provider_enabled"
        private const val DEVICE_SENT_ENABLED = "device_sent_enabled"
    }
}
