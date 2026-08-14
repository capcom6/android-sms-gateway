package me.capcom.smsgateway.modules.encryption.providers

import android.util.Base64
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

abstract class BasePassphraseEncryptionProvider(
    protected val settings: EncryptionSettings,
) : EncryptionProvider {

    final override suspend fun decrypt(encryptedText: String): String {
        val chunks = encryptedText.split('$')
        val parsed = parseChunks(chunks)
        if (!parsed.params.containsKey("i")) {
            throw RuntimeException("Missing iteration count")
        }

        val passphrase = requireNotNull(settings.passphrase) { "Passphrase is not set" }
        val secretKey = generateSecretKeyFromPassphrase(
            passphrase.toCharArray(),
            parsed.salt,
            256,
            parsed.params.getValue("i").toInt()
        )

        return decryptText(parsed.ct, secretKey, parsed.iv)
    }

    final override suspend fun encrypt(plainText: String): String {
        val passphrase = requireNotNull(settings.passphrase) { "Passphrase is not set" }

        val (iv, salt) = generateIvAndSalt()
        val secretKey = generateSecretKeyFromPassphrase(
            passphrase.toCharArray(),
            salt,
            256,
            ITERATION_COUNT
        )

        val cipherText = encryptText(plainText, secretKey, iv)

        return formatOutput(encode(iv), encode(salt), encode(cipherText))
    }

    protected abstract fun generateIvAndSalt(): Pair<ByteArray, ByteArray>

    protected abstract fun formatOutput(ivEnc: String, saltEnc: String, ctEnc: String): String

    protected abstract fun parseChunks(chunks: List<String>): Parsed

    protected class Parsed(
        val params: Map<String, String>,
        val iv: ByteArray,
        val salt: ByteArray,
        val ct: String,
    )

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

    protected fun decode(input: String): ByteArray {
        return Base64.decode(input, Base64.DEFAULT)
    }

    protected fun encode(input: ByteArray): String {
        return Base64.encodeToString(input, Base64.NO_WRAP)
    }

    private fun generateSecretKeyFromPassphrase(
        passphrase: CharArray,
        salt: ByteArray,
        keyLength: Int = 256,
        iterationCount: Int = ITERATION_COUNT
    ): SecretKey {
        val keySpec = PBEKeySpec(passphrase, salt, iterationCount, keyLength)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    protected fun parseParams(params: String): Map<String, String> {
        return params.split(',')
            .map { it.split('=', limit = 2) }
            .associate { it[0] to it[1] }
    }

    protected fun paramsString(): String = "i=$ITERATION_COUNT"

    companion object {
        const val SALT_LENGTH = 16
        const val ITERATION_COUNT = 300_000
    }
}
