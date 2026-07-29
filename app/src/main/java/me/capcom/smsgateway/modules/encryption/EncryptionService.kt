package me.capcom.smsgateway.modules.encryption

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionService(
    private val settings: EncryptionSettings,
    private val e2eKeyService: E2EKeyService,
) {
    suspend fun decrypt(encryptedText: String): String {
        val chunks = encryptedText.split('$')
        if (chunks.size < 3) {
            throw RuntimeException("Invalid encrypted data format")
        }

        val algorithm = chunks[1]
        return when (algorithm) {
            PASSPHRASE_FORMAT -> decryptPassphrase(chunks)
            E2E_FORMAT -> decryptE2E(chunks)
            else -> throw RuntimeException("Unsupported algorithm: $algorithm")
        }
    }

    private fun decryptPassphrase(chunks: List<String>): String {
        if (chunks.size < 5) {
            throw RuntimeException("Invalid passphrase encrypted data format")
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

    private suspend fun decryptE2E(chunks: List<String>): String {
        // Format: $rsa-oaep-aes-256-gcm$v=1$k=N$<base64(encrypted_aes_key)>$<base64(iv)>$<base64(ciphertext)>
        if (chunks.size < 7) {
            throw RuntimeException("Invalid E2E encrypted data format: expected at least 7 chunks")
        }

        val versionParam = chunks[2]
        val version = versionParam.removePrefix("v=")
        if (version != E2E_VERSION) {
            throw RuntimeException("Unsupported E2E version: $version")
        }

        val keyVersionParam = chunks[3]
        val keyVersion = keyVersionParam.removePrefix("k=").toIntOrNull()
            ?: throw RuntimeException("Invalid E2E key version: $keyVersionParam")

        val encryptedAesKey = Base64.decode(chunks[4], Base64.DEFAULT)
        val iv = Base64.decode(chunks[5], Base64.DEFAULT)
        val ciphertext = Base64.decode(chunks[6], Base64.DEFAULT)

        // 1. Decrypt AES key with RSA private key (using specific key version)
        val privateKey = e2eKeyService.getPrivateKey(keyVersion)
            ?: throw RuntimeException("No E2E private key available for version $keyVersion")

        val rsaCipher = Cipher.getInstance(RSA_OAEP_ALGORITHM)
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)

        // 2. Decrypt payload with AES-GCM
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        val aesCipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
        val decryptedBytes = aesCipher.doFinal(ciphertext)

        return String(decryptedBytes)
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

    companion object {
        internal const val PASSPHRASE_FORMAT = "aes-256-cbc/pbkdf2-sha1"
        internal const val E2E_FORMAT = "rsa-oaep-aes-256-gcm"
        private const val E2E_VERSION = "1"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val RSA_OAEP_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    }
}