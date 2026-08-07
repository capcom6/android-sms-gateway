package me.capcom.smsgateway.modules.encryption

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Abstraction over key material storage so the rotation logic in
 * [E2EKeyService] can be unit-tested on the JVM.
 *
 * On API 23+ keys live in the AndroidKeyStore (non-exportable); on API 21-22
 * a software keypair is stored as an AES-GCM encrypted blob in Room.
 */
interface EncryptionKeyStore {
    /**
     * Generates a new RSA-2048 keypair under [alias].
     * [KeyPairResult.persistedBlob] is what gets stored in Room (empty on API 23+).
     */
    fun generateKeyPair(alias: String): KeyPairResult

    /**
     * Loads the private key for [alias], using [persistedBlob] from Room on
     * API 21-22. Returns null when the key cannot be found.
     *
     * [KeyLoadResult.upgradedBlob] is non-null when the stored blob used the
     * legacy derivation and was re-encrypted to the PBKDF2 format; the
     * caller must persist it to Room.
     */
    fun getPrivateKey(alias: String, persistedBlob: ByteArray): KeyLoadResult?

    /** Permanently removes the key material under [alias]. */
    fun delete(alias: String)
}

data class KeyPairResult(
    val keyPair: KeyPair,
    val persistedBlob: ByteArray,
    val publicKeyBase64: String,
)

data class KeyLoadResult(
    val privateKey: PrivateKey,
)

class AndroidEncryptionKeyStore(
    private val context: Context,
) : EncryptionKeyStore {

    private val storageCipher: StorageBlobCipher by lazy {
        StorageBlobCipher(legacyKeyMaterial())
    }

    override fun generateKeyPair(alias: String): KeyPairResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val keyPair = generateKeyPairInKeystore(alias)
            KeyPairResult(
                keyPair,
                ByteArray(0), // private key lives in AndroidKeyStore
                encodePublicKey(keyPair),
            )
        } else {
            val keyPair = generateKeyPairSoftware()
            KeyPairResult(
                keyPair,
                encryptPrivateKeyForStorage(keyPair.private),
                encodePublicKey(keyPair),
            )
        }
    }

    override fun getPrivateKey(alias: String, persistedBlob: ByteArray): KeyLoadResult? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getKeyFromKeystore(alias)?.let { KeyLoadResult(it) }
        } else {
            if (persistedBlob.isEmpty()) return null
            val decrypted = decryptPrivateKeyFromStorage(persistedBlob)
            val keySpec = PKCS8EncodedKeySpec(decrypted.plaintext)
            val privateKey = KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(keySpec)
            KeyLoadResult(privateKey)
        }
    }

    override fun delete(alias: String) {
        deleteKeyFromKeystore(alias)
    }

    private fun encodePublicKey(keyPair: KeyPair): String {
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    // -------------------------------------------------------------------------
    // API 23+ : AndroidKeyStore
    // -------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateKeyPairInKeystore(alias: String): KeyPair {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT,
        )
            .setKeySize(2048)
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        kpg.initialize(spec)
        return kpg.generateKeyPair()
    }

    private fun getKeyFromKeystore(alias: String): PrivateKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        return ks.getKey(alias, null) as? PrivateKey
    }

    private fun deleteKeyFromKeystore(alias: String) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        ks.deleteEntry(alias)
    }

    // -------------------------------------------------------------------------
    // API 21-22 : Software keypair, encrypted private key in Room
    // -------------------------------------------------------------------------

    private fun generateKeyPairSoftware(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        kpg.initialize(2048)
        return kpg.generateKeyPair()
    }

    private fun encryptPrivateKeyForStorage(privateKey: PrivateKey): ByteArray {
        return storageCipher.encrypt(privateKey.encoded)
    }

    private fun decryptPrivateKeyFromStorage(encryptedBlob: ByteArray): BlobDecryptResult {
        return storageCipher.decrypt(encryptedBlob)
    }

    /**
     * Per-device secret material for the API 21-22 software-key derivation.
     * The same ANDROID_ID:packageName string seeds both the legacy SHA-256
     * derivation (obfuscation only - see [StorageBlobCipher]) and the PBKDF2
     * KDF used for v1 blobs.
     */
    @SuppressLint("HardwareIds")
    private fun legacyKeyMaterial(): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ) ?: "fallback"
        return "$androidId:${context.packageName}"
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val RSA_ALGORITHM = "RSA"
    }
}
