package me.capcom.smsgateway.modules.encryption.providers

interface EncryptionProvider {
    suspend fun decrypt(encryptedText: String): String
}
