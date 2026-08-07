package me.capcom.smsgateway.modules.encryption

import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class EncryptionSettings(
    private val storage: KeyValueStorage,
) : Importer {
    val passphrase: String?
        get() = storage.get<String>(PASSPHRASE)

    private var version: Int
        get() = storage.get<Int>(VERSION) ?: 0
        set(value) = storage.set(VERSION, value)

    /**
     * Automatic E2E key rotation interval in days; null or <= 0 disables it.
     */
    var rotationIntervalDays: Int?
        get() = storage.get<Int>(ROTATION_INTERVAL_DAYS)
        set(value) = storage.set(ROTATION_INTERVAL_DAYS, value)

    init {
        migrate()
    }

    private fun migrate() {
        if (version == VERSION_CODE) {
            return
        }

        if (version < 1) {
            passphrase?.let {
                storage.set(PASSPHRASE, it)
            }
        }

        version = VERSION_CODE
    }

    companion object {
        private const val VERSION_CODE = 1

        private const val PASSPHRASE = "passphrase"

        private const val VERSION = "version"

        private const val ROTATION_INTERVAL_DAYS = "rotate_interval_days"
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map {
            when (it.key) {
                PASSPHRASE -> {
                    val newValue = it.value?.toString()
                    val changed = passphrase != newValue
                    storage.set(it.key, newValue)
                    changed
                }

                else -> false
            }
        }.any { it }
    }
}
