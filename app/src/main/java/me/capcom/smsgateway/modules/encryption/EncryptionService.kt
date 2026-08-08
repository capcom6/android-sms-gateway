package me.capcom.smsgateway.modules.encryption

import me.capcom.smsgateway.modules.encryption.providers.EncryptionProviderFactory

class EncryptionService() {
    suspend fun decrypt(encryptedText: String): String {
        val chunks = encryptedText.split('$')
        if (chunks.size < 3) {
            throw RuntimeException("Invalid encrypted data format")
        }

        val algorithm = chunks[1]

        return EncryptionProviderFactory.create(algorithm).decrypt(encryptedText)
    }
}