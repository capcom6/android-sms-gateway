package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class MessagesSettings(
    private val storage: KeyValueStorage,
) : Exporter, Importer {
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

    enum class ProcessingOrder {
        LIFO, FIFO
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

    val processingOrder: ProcessingOrder
        get() = storage.get<ProcessingOrder>(PROCESSING_ORDER) ?: ProcessingOrder.LIFO

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

        private const val PROCESSING_ORDER = "processing_order"
    }

    override fun export(): Map<String, *> {
        return mapOf(
            SEND_INTERVAL_MIN to sendIntervalMin,
            SEND_INTERVAL_MAX to sendIntervalMax,
            LIMIT_PERIOD to limitPeriod,
            LIMIT_VALUE to limitValue,
            SIM_SELECTION_MODE to simSelectionMode,
            LOG_LIFETIME_DAYS to logLifetimeDays,
            PROCESSING_ORDER to processingOrder,
        )
    }

    override fun import(data: Map<String, *>) {
        data.forEach { (key, value) ->
            when (key) {
                SEND_INTERVAL_MIN -> storage.set(
                    key,
                    value?.toString()?.toFloat()?.toInt()?.toString()
                )

                SEND_INTERVAL_MAX -> storage.set(
                    key,
                    value?.toString()?.toFloat()?.toInt()?.toString()
                )

                LIMIT_PERIOD -> storage.set(key, value?.let { Period.valueOf(it.toString()) })
                LIMIT_VALUE -> {
                    val limitValue = value?.toString()?.toInt()
                    if (limitValue != null && limitValue < 1) {
                        throw IllegalArgumentException("Limit value must be >= 1")
                    }
                    storage.set(key, limitValue?.toString())
                }

                SIM_SELECTION_MODE -> storage.set(
                    key,
                    value?.let { SimSelectionMode.valueOf(it.toString()) })

                PROCESSING_ORDER -> storage.set(
                    key,
                    value?.let { ProcessingOrder.valueOf(it.toString()) })

                LOG_LIFETIME_DAYS -> {
                    val logLifetimeDays = value?.toString()?.toInt()
                    if (logLifetimeDays != null && logLifetimeDays < 1) {
                        throw IllegalArgumentException("Log lifetime days must be >= 1")
                    }
                    storage.set(key, logLifetimeDays?.toString())
                }
            }
        }
    }
}