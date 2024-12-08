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

    enum class SimSelectionMode {
        OSDefault,
        RoundRobin,
        Random
    }

    private var version: Int
        get() = storage.get<Int>(VERSION) ?: 0
        set(value) = storage.set(VERSION, value)

    val sendIntervalRange: IntRange?
        get() {
            val min = sendIntervalMin
            val max = sendIntervalMax
            return when {
                max == null -> null
                min > max -> null
                else -> min..max
            }
        }
    private val sendIntervalMin: Int
        get() = storage.get<Int>(SEND_INTERVAL_MIN) ?: 0
    private val sendIntervalMax: Int?
        get() = storage.get<Int>(SEND_INTERVAL_MAX)

    val limitEnabled: Boolean
        get() = limitValue > 0 && limitPeriod != Period.Disabled
    val limitPeriod: Period
        get() = storage.get<Period>(LIMIT_PERIOD) ?: Period.Disabled
    val limitValue: Int
        get() = storage.get(LIMIT_VALUE) ?: 0

    val simSelectionMode: SimSelectionMode
        get() = storage.get<SimSelectionMode>(SIM_SELECTION_MODE) ?: SimSelectionMode.OSDefault

    val logLifetimeDays: Int?
        get() = storage.get<Int?>(LOG_LIFETIME_DAYS)?.takeIf { it > 0 }

    init {
        migrate()
    }

    private fun migrate() {
        if (version == VERSION_CODE) {
            return
        }

        if (version < 1) {
            val SECONDS_BETWEEN_MESSAGES = "SECONDS_BETWEEN_MESSAGES"

            storage.set(SEND_INTERVAL_MAX, storage.get<Int>(SECONDS_BETWEEN_MESSAGES)?.toString())
        }

        version = VERSION_CODE
    }

    companion object {
        private const val VERSION_CODE = 1
        private const val VERSION = "version"

        private const val SEND_INTERVAL_MIN = "send_interval_min"
        private const val SEND_INTERVAL_MAX = "send_interval_max"

        private const val LIMIT_PERIOD = "limit_period"
        private const val LIMIT_VALUE = "limit_value"

        private const val SIM_SELECTION_MODE = "sim_selection_mode"

        private const val LOG_LIFETIME_DAYS = "log_lifetime_days"
    }
}