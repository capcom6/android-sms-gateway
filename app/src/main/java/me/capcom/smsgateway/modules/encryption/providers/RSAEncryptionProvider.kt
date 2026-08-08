package me.capcom.smsgateway.modules.encryption.providers

import android.util.Base64
import me.capcom.smsgateway.modules.device.DeviceService
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class RSAEncryptionProvider(
    private val deviceService: DeviceService,
) : EncryptionProvider {
    override suspend fun encrypt(plainText: String): String {
        val deviceKey = deviceService.ensureKey()
            ?: throw RuntimeException("No E2E key pair available")
        val publicKey = KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(
            X509EncodedKeySpec(Base64.decode(deviceKey.publicKeyBase64, Base64.NO_WRAP)),
        )

        val random = SecureRandom()
        val aesKeyBytes = ByteArray(AES_KEY_LENGTH)
        val iv = ByteArray(GCM_IV_LENGTH)
        random.nextBytes(aesKeyBytes)
        random.nextBytes(iv)

        val rsaCipher = Cipher.getInstance(RSA_OAEP_ALGORITHM)
        rsaCipher.init(
            Cipher.ENCRYPT_MODE,
            publicKey,
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT,
            ),
        )
        val encryptedAesKey = rsaCipher.doFinal(aesKeyBytes)

        val aesKey = SecretKeySpec(aesKeyBytes, AES_ALGORITHM)
        val aesCipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        aesCipher.init(
            Cipher.ENCRYPT_MODE,
            aesKey,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        val ciphertext = aesCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return listOf(
            "v=$E2E_VERSION",
            "k=${deviceKey.keyVersion}",
            Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        ).joinToString("$")
    }

    override suspend fun decrypt(encryptedText: String): String {
        val chunks = encryptedText.split('$')
        // Format: v=1$k=N$<base64(encrypted_aes_key)>$<base64(iv)>$<base64(ciphertext)>
        if (chunks.size < 5) {
            throw RuntimeException("Invalid E2E encrypted data format: expected at least 7 chunks")
        }

        val versionParam = chunks[0]
        val version = versionParam.removePrefix("v=")
        if (version != E2E_VERSION) {
            throw RuntimeException("Unsupported E2E version: $version")
        }

        val keyVersionParam = chunks[1]
        val keyVersion = keyVersionParam.removePrefix("k=").toIntOrNull()
            ?: throw RuntimeException("Invalid E2E key version: $keyVersionParam")

        val encryptedAesKey = Base64.decode(chunks[2], Base64.DEFAULT)
        val iv = Base64.decode(chunks[3], Base64.DEFAULT)
        val ciphertext = Base64.decode(chunks[4], Base64.DEFAULT)

        // 1. Decrypt AES key with RSA private key (using specific key version)
        val privateKey = deviceService.getPrivateKey(keyVersion)
            ?: throw RuntimeException("No E2E private key available for version $keyVersion")

        val rsaCipher = Cipher.getInstance(RSA_OAEP_ALGORITHM)
        rsaCipher.init(
            Cipher.DECRYPT_MODE,
            privateKey,
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT,
            ),
        )
        val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)

        // 2. Decrypt payload with AES-GCM
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        val aesCipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
        val decryptedBytes = aesCipher.doFinal(ciphertext)

        return String(decryptedBytes)
    }

    companion object {
        private const val E2E_VERSION = "1"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH = 12
        private const val AES_KEY_LENGTH = 32
        private const val RSA_ALGORITHM = "RSA"
        private const val AES_ALGORITHM = "AES"
        private const val RSA_OAEP_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    }
}
