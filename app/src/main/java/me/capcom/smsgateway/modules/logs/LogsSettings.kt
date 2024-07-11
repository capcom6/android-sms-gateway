package me.capcom.smsgateway.modules.logs

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class LogsSettings(
    private val storage: KeyValueStorage,
) {
    val lifetimeDays: Int?
        get() = storage.get<Int?>(LIFETIME_DAYS)?.takeIf { it > 0 }

    companion object {
        private const val LIFETIME_DAYS = "LIFETIME_DAYS"
    }
}