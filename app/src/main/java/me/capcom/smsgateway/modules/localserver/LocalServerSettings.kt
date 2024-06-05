package me.capcom.smsgateway.modules.localserver

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class LocalServerSettings(
    private val storage: KeyValueStorage,
) {
    var enabled: Boolean
        get() = storage.get<Boolean>(ENABLED) ?: false
        set(value) = storage.set(ENABLED, value)

    companion object {
        private const val ENABLED = "ENABLED"
    }
}