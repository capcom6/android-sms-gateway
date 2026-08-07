package me.capcom.smsgateway.modules.encryption

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.modules.encryption.db.EncryptionKey
import me.capcom.smsgateway.modules.encryption.db.EncryptionKeysDao
import me.capcom.smsgateway.modules.encryption.workers.KeyRotationWorker
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import java.security.PrivateKey
import java.util.concurrent.ConcurrentHashMap

class E2EKeyService(
    private val keyStore: EncryptionKeyStore,
    private val dao: EncryptionKeysDao,
    private val logsSvc: LogsService,
    private val settings: EncryptionSettings,
) {
    private val rotationMutex = Mutex()

    /**
     * In-memory cache of loaded [PrivateKey]s keyed by keyVersion. A batch of
     * N values decrypting with the same keyVersion performs exactly 1 Room
     * lookup and 1 keystore load; the loaded key is reused for the rest.
     *
     * ConcurrentHashMap so invalidations from rotate/retire/cleanup (which do
     * not hold [rotationMutex]) are safe; the load path in [getPrivateKey] is
     * double-checked under [rotationMutex] so concurrent batches load once.
     * Entries are removed on rotate/retire/cleanup so no stale private key
     * material is retained after it is no longer active.
     */
    private val privateKeyCache = ConcurrentHashMap<Int, PrivateKey>()

    /**
     * Ensures the device has an E2E keypair. Generates one if missing.
     * Returns the current [EncryptionKey] or null if generation failed.
     */
    suspend fun ensureKey(): EncryptionKey? {
        return rotationMutex.withLock {
            val existing = getCurrentKey()
            if (existing != null) return@withLock existing

            try {
                rotateKeyLocked()
            } catch (e: Exception) {
                logsSvc.insert(
                    LogEntry.Priority.WARN,
                    MODULE_NAME,
                    "Failed to rotate device key",
                    e,
                )
                return@withLock null
            }
        }
    }

    /**
     * Generates a new RSA-2048 keypair and stores it, then retires the
     * previously active key. The retired key stays decryptable (its key
     * material is kept) until [cleanupOldKeys] purges it after the retention
     * period, so in-flight messages to older versions still decrypt.
     *
     * Returns the newly created [EncryptionKey]. Throws on failure.
     */
    suspend fun rotateKey(): EncryptionKey {
        return rotationMutex.withLock {
            rotateKeyLocked()
        }
    }

    fun start(context: Context) {
        KeyRotationWorker.start(context)
    }

    fun stop(context: Context) {
        KeyRotationWorker.stop(context)
    }

    /**
     * Rotates the active E2E key when the configured rotation interval has
     * elapsed since the current key was created. No-op when rotation is
     * disabled (interval null or <= 0). Creates a baseline key when none
     * exists so future due-checks have an anchor. The interval is re-read
     * from settings on every invocation.
     */
    suspend fun rotateKeyIfDue() {
        val intervalDays = settings.rotationIntervalDays ?: return
        if (intervalDays <= 0) return

        val current = getCurrentKey()
        if (current == null) {
            ensureKey()
            return
        }

        rotationMutex.withLock {
            rotateKeyIfDueLocked(intervalDays)
        }
    }

    /**
     * Due-check + rotation under [rotationMutex]: the current key is re-read
     * inside the lock so the due-decision is atomic with the rotation and a
     * concurrent manual [rotateKey] cannot cause a double rotation. Calls
     * [rotateKeyLocked] directly because [rotateKey] re-acquires
     * [rotationMutex] and Kotlin Mutex is not reentrant.
     */
    private suspend fun rotateKeyIfDueLocked(intervalDays: Int) {
        val current = getCurrentKey() ?: return
        if (current.createdAt + intervalDays * 86400000L <= System.currentTimeMillis()) {
            rotateKeyLocked()
        }
    }

    private suspend fun rotateKeyLocked(): EncryptionKey = withContext(Dispatchers.IO) {
        val nextVersion = (dao.getAll().firstOrNull()?.keyVersion ?: 0) + 1
        val alias = keyAlias(nextVersion)

        val generated = keyStore.generateKeyPair(alias)
        val entity = EncryptionKey(
            keyVersion = nextVersion,
            privateKeyBlob = generated.persistedBlob,
            publicKeyBase64 = generated.publicKeyBase64,
        )

        // Persist the new key first so there is never a window with no
        // active key, then retire the old one.
        val current = dao.getCurrent()
        val id = try {
            dao.insert(entity)
        } catch (e: Exception) {
            // Remove the keystore entry so it is not orphaned when persistence fails
            try {
                keyStore.delete(alias)
            } catch (_: Exception) {
                // best-effort cleanup; original failure is what matters
            }
            throw e
        }

        current?.let { retireKey(it.keyVersion) }

        val saved = entity.copy(id = id)
        try {
            enforceKeyLimit()
        } catch (e: Exception) {
            logsSvc.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Failed to enforce key limit",
                e,
            )
        }
        // A new version is active and old ones were retired: never serve stale
        // private key material from before the rotation.
        privateKeyCache.clear()
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
        // Fast path: already loaded in this process for this keyVersion.
        privateKeyCache[keyVersion]?.let { return@withContext it }

        rotationMutex.withLock {
            // Double-checked: another batch may have loaded it while we waited.
            privateKeyCache[keyVersion]?.let { return@withLock it }

            val record = dao.getByKeyVersion(keyVersion) ?: return@withLock null
            val loaded = keyStore.getPrivateKey(keyAlias(record.keyVersion), record.privateKeyBlob)
                ?: return@withLock null
            loaded.privateKey.also { privateKeyCache[keyVersion] = it }
        }
    }

    /**
     * Checks if the device has reached the maximum number of active keys.
     * If so, retires the oldest ones (Room-only; key material is retained so
     * old keys remain decryptable) and purges entries past the retention
     * period.
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
     * Retires the key with the given [keyVersion]: marks the Room row retired
     * only. The AndroidKeyStore entry is intentionally left intact so
     * messages encrypted to this version remain decryptable; it is removed
     * later by [cleanupOldKeys] after the 7-day retention cutoff.
     */
    private suspend fun retireKey(keyVersion: Int) {
        dao.retire(keyVersion)
        // The key is no longer active; drop any cached material for it.
        privateKeyCache.remove(keyVersion)
    }

    /**
     * Deletes keys that are retired and older than 7 days: removes the
     * AndroidKeyStore entry and the Room row in the same pass.
     */
    internal suspend fun cleanupOldKeys() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val expired = dao.getRetiredOlderThan(cutoff)
        if (expired.isEmpty()) return@withContext

        for (key in expired) {
            try {
                keyStore.delete(keyAlias(key.keyVersion))
            } catch (e: Exception) {
                logsSvc.insert(
                    LogEntry.Priority.WARN,
                    MODULE_NAME,
                    "Failed to delete keystore entry for key ${key.keyVersion}",
                    e,
                )
            }
            // Key material is gone from both Room and the keystore: drop the
            // cached private key so it is not retained in memory.
            privateKeyCache.remove(key.keyVersion)
        }
        dao.deleteOld(cutoff)
    }

    private fun keyAlias(version: Int): String = "e2e_key_v$version"

    companion object {
        private const val MAX_ACTIVE_KEYS = 3
        private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
