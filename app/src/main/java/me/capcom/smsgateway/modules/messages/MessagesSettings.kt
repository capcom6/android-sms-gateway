package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class MessagesSettings(
    private val storage: KeyValueStorage,
) {
    enum class Period(val duration: Long) {
        Disabled(0L),
        PerMinute(60000L),
        PerHour(3600000L),
        PerDay(86400000L),
    }

    val secondsBetweenMessages: Int
        get() = storage.get<Int>(SECONDS_BETWEEN_MESSAGES) ?: 0

    val limitEnabled: Boolean
        get() = limitValue > 0 && limitPeriod != Period.Disabled
    val limitPeriod: Period
        get() = storage.get<Period>(LIMIT_PERIOD) ?: Period.Disabled
    val limitValue: Int
        get() = storage.get(LIMIT_VALUE) ?: 0

    companion object {
        private const val SECONDS_BETWEEN_MESSAGES = "SECONDS_BETWEEN_MESSAGES"

        private const val LIMIT_PERIOD = "limit_period"
        private const val LIMIT_VALUE = "limit_value"
    }
}