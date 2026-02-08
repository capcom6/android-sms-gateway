package me.stappmus.messagegateway.modules.messages

import me.stappmus.messagegateway.modules.settings.Exporter
import me.stappmus.messagegateway.modules.settings.Importer
import me.stappmus.messagegateway.modules.settings.KeyValueStorage
import me.stappmus.messagegateway.modules.settings.get

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
            LIMIT_PERIOD to limitPeriod.name,
            LIMIT_VALUE to limitValue,
            SIM_SELECTION_MODE to simSelectionMode.name,
            LOG_LIFETIME_DAYS to logLifetimeDays,
            PROCESSING_ORDER to processingOrder.name,
        )
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map {
            val key = it.key
            val value = it.value

            when (key) {
                SEND_INTERVAL_MIN -> {
                    val newValue = value?.toString()?.toFloat()?.toInt()
                    val changed = this.sendIntervalMin != newValue

                    storage.set(
                        key,
                        newValue?.toString()
                    )

                    changed
                }

                SEND_INTERVAL_MAX -> {
                    val newValue = value?.toString()?.toFloat()?.toInt()
                    val changed = this.sendIntervalMax != newValue

                    storage.set(
                        key,
                        newValue?.toString()
                    )

                    changed
                }

                LIMIT_PERIOD -> {
                    val newValue = value?.let { Period.valueOf(it.toString()) } ?: Period.Disabled
                    val changed = this.limitPeriod != newValue

                    storage.set(key, newValue.name)

                    changed
                }

                LIMIT_VALUE -> {
                    val limitValue = value?.toString()?.toFloat()?.toInt()
                    if (limitValue != null && limitValue < 0) {
                        throw IllegalArgumentException("Limit value must be >= 0")
                    }

                    val changed = this.limitValue != (limitValue ?: 0)

                    storage.set(key, limitValue?.toString())

                    changed
                }

                SIM_SELECTION_MODE -> {
                    val newValue = value?.let { SimSelectionMode.valueOf(it.toString()) }
                        ?: SimSelectionMode.OSDefault
                    val changed = this.simSelectionMode != newValue

                    storage.set(key, newValue.name)

                    changed
                }

                PROCESSING_ORDER -> {
                    val newValue = value?.let { ProcessingOrder.valueOf(it.toString()) }
                        ?: ProcessingOrder.LIFO
                    val changed = this.processingOrder != newValue

                    storage.set(key, newValue.name)

                    changed
                }

                LOG_LIFETIME_DAYS -> {
                    val logLifetimeDays = value?.toString()?.toFloat()?.toInt()
                    if (logLifetimeDays != null && logLifetimeDays < 1) {
                        throw IllegalArgumentException("Log lifetime days must be >= 1")
                    }

                    val changed = this.logLifetimeDays != logLifetimeDays
                    storage.set(key, logLifetimeDays?.toString())

                    changed
                }

                else -> false
            }
        }.any { it }
    }
}