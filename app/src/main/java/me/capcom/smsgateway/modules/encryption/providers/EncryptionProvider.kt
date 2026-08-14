package me.capcom.smsgateway.modules.encryption.providers

interface EncryptionProvider {
    suspend fun decrypt(encryptedText: String): String
    suspend fun encrypt(plainText: String): String
}
