package me.capcom.smsgateway.modules.encryption

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.modules.encryption.db.EncryptionKey
import me.capcom.smsgateway.modules.encryption.db.EncryptionKeysDao
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class E2EKeyService(
    private val context: Context,
    private val dao: EncryptionKeysDao,
    private val logsSvc: LogsService,
) {
    private val rotationMutex = Mutex()

    /**
     * Ensures the device has an E2E keypair. Generates one if missing.
     * Returns the public key base64 or null if generation failed.
     */
    suspend fun ensureKey(): EncryptionKey? {
        return rotationMutex.withLock {
            val existing = getCurrentKey()
            if (existing != null) return@withLock existing

            try {
                rotateKey()
            } catch (e: Exception) {
                logsSvc.insert(
                    LogEntry.Priority.WARN,
                    MODULE_NAME,
                    "Failed to rotate device key",
                    mapOf("exception" to e),
                )
                return@withLock null
            }
        }
    }

    /**
     * Generates a new RSA-2048 keypair and stores it.
     * Retires the current active key if one exists.
     * Returns the newly created [EncryptionKey].
     */
    private suspend fun rotateKey(): EncryptionKey = withContext(Dispatchers.IO) {
        val nextVersion = (dao.getAll().firstOrNull()?.keyVersion ?: 0) + 1
        val alias = keyAlias(nextVersion)

        val keyPair = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            generateKeyPairInKeystore(alias)
        } else {
            generateKeyPairSoftware()
        }

        // Retire current active key (also deletes its AndroidKeyStore entry)
        dao.getCurrent()?.let { current ->
            retireKey(current.keyVersion)
        }

        val privateKeyBlob = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ByteArray(0) // stored in AndroidKeyStore
        } else {
            encryptPrivateKeyForStorage(keyPair.private)
        }

        val publicKeyBase64 = Base64.encodeToString(
            keyPair.public.encoded,
            Base64.NO_WRAP,
        )

        val entity = EncryptionKey(
            keyVersion = nextVersion,
            privateKeyBlob = privateKeyBlob,
            publicKeyBase64 = publicKeyBase64,
        )
        val id = try {
            dao.insert(entity)
        } catch (e: Exception) {
            // Remove the keystore entry so it is not orphaned when persistence fails
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                deleteKeyFromKeystore(alias)
            }
            throw e
        }

        val saved = entity.copy(id = id)
        try {
            enforceKeyLimit()
        } catch (e: Exception) {
            logsSvc.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Failed to enforce key limit",
                mapOf("exception" to e),
            )
        }
        saved
    }

    /**
     * Returns the current active [EncryptionKey], or null if no key exists.
     */
    private suspend fun getCurrentKey(): EncryptionKey? = withContext(Dispatchers.IO) {
        dao.getCurrent()
    }

    /**
     * Returns the [PrivateKey] for the given [keyVersion], or the current
     * active key when null. Returns null if the key cannot be found or loaded.
     */
    suspend fun getPrivateKey(keyVersion: Int): PrivateKey? = withContext(Dispatchers.IO) {
        val record = dao.getByKeyVersion(keyVersion) ?: return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getKeyFromKeystore(keyAlias(record.keyVersion))
        } else {
            if (record.privateKeyBlob.isEmpty()) return@withContext null
            decryptPrivateKeyFromStorage(record.privateKeyBlob)
        }
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
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        return kpg.generateKeyPair()
    }

    private fun encryptPrivateKeyForStorage(privateKey: PrivateKey): ByteArray {
        val secretKey = deriveStorageKey()
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(privateKey.encoded)
        return iv + encrypted
    }

    private fun decryptPrivateKeyFromStorage(encryptedBlob: ByteArray): PrivateKey {
        val secretKey = deriveStorageKey()
        val cipher = Cipher.getInstance(AES_GCM)
        val iv = encryptedBlob.copyOf(GCM_IV_LENGTH)
        val encrypted = encryptedBlob.copyOfRange(GCM_IV_LENGTH, encryptedBlob.size)
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val keyBytes = cipher.doFinal(encrypted)

        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    /**
     * Derives a 256-bit AES key from ANDROID_ID + package name.
     * Not hardware-backed; provides at-rest encryption only.
     */
    @SuppressLint("HardwareIds")
    private fun deriveStorageKey(): SecretKeySpec {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ) ?: "fallback"
        val keyMaterial = "$androidId:${context.packageName}".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(keyMaterial).copyOf(AES_KEY_LENGTH)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Checks if the device has reached the maximum number of active keys.
     * If so, cleans up old ones.
     */
    suspend fun enforceKeyLimit() = withContext(Dispatchers.IO) {
        val activeKeys = dao.getAllActive()
        if (activeKeys.size <= MAX_ACTIVE_KEYS) return@withContext

        // Keep the latest MAX_ACTIVE_KEYS, retire the rest
        val toRetire = activeKeys.drop(MAX_ACTIVE_KEYS)
        for (key in toRetire) {
            retireKey(key.keyVersion)
        }
        cleanupOldKeys()

        return@withContext
    }

    /**
     * Retires the key with the given [keyVersion].
     */
    private suspend fun retireKey(keyVersion: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            deleteKeyFromKeystore(keyAlias(keyVersion))
        }
        dao.retire(keyVersion)
    }

    /**
     * Deletes keys that are retired and older than 7 days.
     */
    private suspend fun cleanupOldKeys() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        dao.deleteOld(cutoff)
    }

    private fun keyAlias(version: Int): String = "e2e_key_v$version"

    companion object {
        private const val TAG = "E2EKeyService"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val AES_KEY_LENGTH = 32
        private const val MAX_ACTIVE_KEYS = 3
    }
}
