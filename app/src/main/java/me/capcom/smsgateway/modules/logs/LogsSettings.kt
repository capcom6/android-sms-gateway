package me.capcom.smsgateway.modules.logs

import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class LogsSettings(
    private val storage: KeyValueStorage,
) : Exporter, Importer {
    val lifetimeDays: Int?
        get() = storage.get<Int?>(LIFETIME_DAYS)?.takeIf { it > 0 }

    private var version: Int
        get() = storage.get<Int>(VERSION) ?: 0
        set(value) = storage.set(VERSION, value)

    init {
        migrate()
    }

    private fun migrate() {
        if (version == VERSION_CODE) {
            return
        }

        if (version < 1) {
            storage.set(LIFETIME_DAYS, "30")
        }

        version = VERSION_CODE
    }

    companion object {
        private const val LIFETIME_DAYS = "lifetime_days"

        private const val VERSION_CODE = 1
        private const val VERSION = "version"
    }

    override fun export(): Map<String, *> {
        return mapOf(
            LIFETIME_DAYS to lifetimeDays,
        )
    }

    override fun import(data: Map<String, *>) {
        data.forEach { (key, value) ->
            when (key) {
                LIFETIME_DAYS -> storage.set(key, value?.toString()?.toFloat()?.toInt()?.toString())
            }
        }
    }
}