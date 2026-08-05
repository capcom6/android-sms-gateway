package me.capcom.smsgateway.modules.encryption

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionService(
    private val settings: EncryptionSettings,
) {
    /**
     * Batch encryption (plan A-A1): derives ONE AES-256 key from the settings
     * passphrase per batch via PBKDF2WithHmacSHA1 (random 16-byte salt), then
     * encrypts each field with its own random 16-byte IV using
     * AES/CBC/PKCS5Padding. Emits the 6-part format:
     * "$aes-256-cbc/pbkdf2-sha1$i=<iters>$<saltB64>$<ivB64>$<cipherB64>".
     *
     * The passphrase comes from the injected [EncryptionSettings]; a missing
     * passphrase fails fast with IllegalArgumentException, same as decrypt().
     *
     * [iterationCount] defaults to 300_000 for production; tests pass a small
     * count (e.g. 1000) to keep derivation fast. [keyFactory] is injectable so
     * callers can observe/count derivations.
     */
    fun encryptBatch(
        plaintexts: List<String>,
        iterationCount: Int = DEFAULT_ITERATION_COUNT,
        keyFactory: (passphrase: String, salt: ByteArray, iterationCount: Int) -> SecretKey = ::deriveBatchKey,
    ): List<String> {
        val passphrase = requireNotNull(settings.passphrase) { "Passphrase is not set" }
        val salt = randomBytes(SALT_SIZE)
        val secretKey = keyFactory(passphrase, salt, iterationCount)

        return plaintexts.map { plaintext ->
            val iv = randomBytes(IV_SIZE)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            buildString {
                append('$').append(ALGORITHM)
                append("\$i=$iterationCount")
                append('$').append(encode(salt))
                append('$').append(encode(iv))
                append('$').append(encode(encryptedBytes))
            }
        }
    }

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

        val iv: ByteArray
        val text: String
        when (chunks.size) {
            5 -> {
                // Legacy 5-part format: the salt doubles as the IV.
                iv = salt
                text = chunks[4]
            }

            6 -> {
                // New 6-part format: explicit per-field IV chunk.
                iv = decode(chunks[4])
                text = chunks[5]
            }

            else -> throw RuntimeException("Invalid encrypted data format")
        }

        val passphrase = requireNotNull(settings.passphrase) { "Passphrase is not set" }
        val secretKey = generateSecretKeyFromPassphrase(
            passphrase.toCharArray(),
            salt,
            256,
            params.getValue("i").toInt()
        )

        return decryptText(text, secretKey, iv)
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
        return deriveSecretKey(passphrase, salt, keyLength, iterationCount)
    }

    private fun parseParams(params: String): Map<String, String> {
        return params.split(',')
            .map { it.split('=', limit = 2) }
            .associate { it[0] to it[1] }
    }

    companion object {
        const val DEFAULT_ITERATION_COUNT = 300_000

        const val ALGORITHM = "aes-256-cbc/pbkdf2-sha1"

        const val SALT_SIZE = 16

        const val IV_SIZE = 16
    }
}

/**
 * Shared PBKDF2WithHmacSHA1 derivation used by both the legacy decrypt path and
 * the batch encryptor, so both produce identical keys for identical inputs.
 */
internal fun deriveSecretKey(
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

internal fun deriveBatchKey(passphrase: String, salt: ByteArray, iterationCount: Int): SecretKey =
    deriveSecretKey(passphrase.toCharArray(), salt, keyLength = 256, iterationCount = iterationCount)

internal fun randomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }

internal fun encode(input: ByteArray): String =
    Base64.encodeToString(input, Base64.NO_WRAP)