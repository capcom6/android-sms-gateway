package me.capcom.smsgateway.modules.encryption.providers

import android.util.Base64
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class PassphraseEncryptionProvider(
    private val settings: EncryptionSettings,
) : EncryptionProvider {
    override suspend fun decrypt(encryptedText: String): String {
        val chunks = encryptedText.split('$')
        if (chunks.size < 3) {
            throw RuntimeException("Invalid passphrase encrypted data format")
        }

        val params = parseParams(chunks[0])
        if (!params.containsKey("i")) {
            throw RuntimeException("Missing iteration count")
        }

        val salt = decode(chunks[1])
        val text = chunks[2]

        val passphrase = requireNotNull(settings.passphrase) { "Passphrase is not set" }
        val secretKey = generateSecretKeyFromPassphrase(
            passphrase.toCharArray(),
            salt,
            256,
            params.getValue("i").toInt()
        )

        return decryptText(text, secretKey, salt)
    }

    override suspend fun encrypt(plainText: String): String {
        val passphrase = requireNotNull(settings.passphrase) { "Passphrase is not set" }

        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)

        val secretKey = generateSecretKeyFromPassphrase(
            passphrase.toCharArray(),
            salt,
            256,
            DEFAULT_ITERATION_COUNT
        )

        val cipherText = encryptText(plainText, secretKey, salt)

        return "i=$DEFAULT_ITERATION_COUNT" + "$" + encode(salt) + "$" + encode(cipherText)
    }

    private fun decryptText(encryptedText: String, secretKey: SecretKey, iv: ByteArray): String {
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val encryptedBytes = decode(encryptedText)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }

    private fun encryptText(plainText: String, secretKey: SecretKey, iv: ByteArray): ByteArray {
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(plainText.toByteArray())
    }

    private fun decode(input: String): ByteArray {
        return Base64.decode(input, Base64.DEFAULT)
    }

    private fun encode(input: ByteArray): String {
        return Base64.encodeToString(input, Base64.NO_WRAP)
    }

    private fun generateSecretKeyFromPassphrase(
        passphrase: CharArray,
        salt: ByteArray,
        keyLength: Int = 256,
        iterationCount: Int = DEFAULT_ITERATION_COUNT
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

    companion object {
        private const val SALT_LENGTH = 16
        private const val DEFAULT_ITERATION_COUNT = 300_000
    }
}
