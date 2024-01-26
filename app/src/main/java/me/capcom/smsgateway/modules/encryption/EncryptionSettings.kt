package me.capcom.smsgateway.modules.encryption

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class EncryptionSettings(
    private val storage: KeyValueStorage,
) {
    var passphrase: String?
        get() = storage.get<String>(PASSPHRASE)
        set(value) = storage.set(PASSPHRASE, value)

    companion object {
        private const val PASSPHRASE = "passphrase"
    }
}