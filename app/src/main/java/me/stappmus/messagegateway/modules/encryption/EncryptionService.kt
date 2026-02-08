package me.stappmus.messagegateway.modules.encryption

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionService(
    private val settings: EncryptionSettings,
) {
    fun decrypt(encryptedText: String): String {
        val chunks = encryptedText.split('$')
        if (chunks.size < 5)
            throw RuntimeException("Invalid encrypted data format")

        if (chunks[1] != "aes-256-cbc/pbkdf2-sha1") {
            throw RuntimeException("Unsupported algorithm")
        }

        val params = parseParams(chunks[2])
        if (!params.containsKey("i")) {
            throw RuntimeException("Missing iteration count")
        }

        val salt = decode(chunks[3])
        val text = chunks[4]

        val passphrase = requireNotNull(settings.passphrase) { "Passphrase is not set" }
        val secretKey = generateSecretKeyFromPassphrase(
            passphrase.toCharArray(),
            salt,
            256,
            params.getValue("i").toInt()
        )

        return decryptText(text, secretKey, salt)
    }

    private fun decryptText(encryptedText: String, secretKey: SecretKey, iv: ByteArray): String {
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val encryptedBytes = decode(encryptedText)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }

    private fun decode(input: String): ByteArray {
        return Base64.decode(input, Base64.DEFAULT)
    }

    private fun generateSecretKeyFromPassphrase(
        passphrase: CharArray,
        salt: ByteArray,
        keyLength: Int = 256,
        iterationCount: Int = 300_000
    ): SecretKey {
        val keySpec = PBEKeySpec(passphrase, salt, iterationCount, keyLength)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun parseParams(params: String): Map<String, String> {
        return params.split(',')
            .map { it.split('=', limit = 2) }
            .associate { it[0] to it[1] }
    }
}