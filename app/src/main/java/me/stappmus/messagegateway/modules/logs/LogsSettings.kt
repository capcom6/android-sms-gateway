package me.stappmus.messagegateway.modules.logs

import me.stappmus.messagegateway.modules.settings.Exporter
import me.stappmus.messagegateway.modules.settings.Importer
import me.stappmus.messagegateway.modules.settings.KeyValueStorage
import me.stappmus.messagegateway.modules.settings.get

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

    override fun import(data: Map<String, *>): Boolean {
        return data.map {
            when (it.key) {
                LIFETIME_DAYS -> {
                    val newValue = it.value?.toString()?.toFloat()?.toInt()?.takeIf { it > 0 }
                    val changed = lifetimeDays != newValue

                    storage.set(it.key, newValue?.toString())

                    changed
                }

                else -> false
            }
        }.any { it }
    }
}