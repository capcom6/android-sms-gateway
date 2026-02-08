package me.stappmus.messagegateway.modules.encryption

import me.stappmus.messagegateway.modules.settings.Importer
import me.stappmus.messagegateway.modules.settings.KeyValueStorage
import me.stappmus.messagegateway.modules.settings.get

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
