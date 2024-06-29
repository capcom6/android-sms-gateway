package me.capcom.smsgateway.modules.ping

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class PingSettings(
    private val storage: KeyValueStorage,
) {
    val enabled: Boolean
        get() = intervalSeconds != null

    var intervalSeconds: Int?
        get() = storage.get<Int>(INTERVAL_SECONDS)?.takeIf { it > 0 }
        set(value) = storage.set(INTERVAL_SECONDS, value)

    companion object {
        //        private const val ENABLED = "enabled"
        private const val INTERVAL_SECONDS = "interval_seconds"
    }
}