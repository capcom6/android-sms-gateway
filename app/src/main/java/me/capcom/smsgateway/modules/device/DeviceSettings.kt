package me.capcom.smsgateway.modules.device

import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class DeviceSettings(
    private val storage: KeyValueStorage,
) : Importer {
    private var version: Int
        get() = storage.get<Int>(VERSION) ?: 0
        set(value) = storage.set(VERSION, value)

    var keyRotationIntervalDays: Int?
        get() = storage.get<Int>(KEY_ROTATION_INTERVAL_DAYS)
        set(value) = storage.set(KEY_ROTATION_INTERVAL_DAYS, value)

    init {
        migrate()
    }

    private fun migrate() {
    }

    companion object {
        private const val VERSION_CODE = 1

        private const val VERSION = "version"

        private const val KEY_ROTATION_INTERVAL_DAYS = "key_rotate_interval_days"
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map {
            when (it.key) {
                KEY_ROTATION_INTERVAL_DAYS -> {
                    val newValue = it.value?.toString()?.toInt()
                    val changed = keyRotationIntervalDays != newValue
                    storage.set(it.key, newValue)
                    changed
                }

                else -> false
            }
        }.any { it }
    }
}
