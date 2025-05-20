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
    }

    override fun import(data: Map<String, *>) {
        data.forEach { (key, value) ->
            when (key) {
                PASSPHRASE -> storage.set(key, value?.toString())
            }
        }
    }
}
