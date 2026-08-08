package me.capcom.smsgateway.modules.device

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.modules.device.db.DeviceKey
import me.capcom.smsgateway.modules.device.db.DeviceKeysDao
import me.capcom.smsgateway.modules.device.events.DeviceKeyRotatedEvent
import me.capcom.smsgateway.modules.device.keys.Fingerprint
import me.capcom.smsgateway.modules.device.keys.KeyStore
import me.capcom.smsgateway.modules.device.workers.KeyRotationWorker
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import java.security.PrivateKey
import java.util.concurrent.ConcurrentHashMap

class DeviceService(
    private val keyStore: KeyStore,
    private val keys: DeviceKeysDao,
    private val logsSvc: LogsService,
    private val settings: DeviceSettings,
    private val events: EventBus,
) {
    private val rotationMutex = Mutex()

    /**
     * In-memory cache of loaded [PrivateKey]s keyed by keyVersion. A batch of
     * N values decrypting with the same keyVersion performs exactly 1 Room
     * lookup and 1 keystore load; the loaded key is reused for the rest.
     *
     * All cache mutations (population in [getPrivateKey], invalidation in
     * rotate/retire/cleanup) happen under [rotationMutex], so an invalidated
     * key can never be re-inserted after its persistent state was removed;
     * [getPrivateKey] is double-checked under [rotationMutex] so concurrent
     * batches load once. The reads outside the lock are safe because entries
     * are only ever added while the key still exists in Room/AndroidKeyStore,
     * and removed the moment it no longer should be served. Entries are
     * removed on rotate/retire/cleanup so no stale private key material is
     * retained after it is no longer active.
     */
    private val privateKeyCache = ConcurrentHashMap<Int, PrivateKey>()

    /**
     * Ensures the device has an E2E keypair. Generates one if missing.
     * Returns the current [EncryptionKey] or null if generation failed.
     */
    suspend fun ensureKey(): DeviceKey? {
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
     * Generates a new RSA-2048 keypair and stores it, then retires all
     * previously active keys. The retired keys stay decryptable (their key
     * material is kept) until [cleanupOldKeys] purges them after the
     * retention period, so in-flight messages to older versions still
     * decrypt.
     *
     * When an existing key is replaced, emits [DeviceKeyRotatedEvent] so the
     * gateway module can publish the new public key and version to the
     * server. Initial key creation emits nothing: the registration and
     * device-update paths already publish the key they create.
     *
     * Returns the newly created [DeviceKey]. Throws on failure.
     */
    suspend fun rotateKey(): DeviceKey {
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
        val intervalDays = settings.keyRotationIntervalDays ?: return
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

    private suspend fun rotateKeyLocked(): DeviceKey = withContext(Dispatchers.IO) {
        val nextVersion = (keys.getAll().firstOrNull()?.keyVersion ?: 0) + 1
        val alias = keyAlias(nextVersion)

        val generated = keyStore.generateKeyPair(alias)
        val entity = DeviceKey(
            keyVersion = nextVersion,
            publicKeyBase64 = generated.publicKeyBase64,
        )

        // Persist the new key first so there is never a window with no
        // active key, then retire the old ones.
        val id = try {
            keys.insert(entity)
        } catch (e: Exception) {
            // Remove the keystore entry so it is not orphaned when persistence fails
            try {
                keyStore.delete(alias)
            } catch (_: Exception) {
                // best-effort cleanup; original failure is what matters
            }
            throw e
        }

        // Retire every previously active key (normally just the previous
        // current one; all of them after an interrupted rotation) so nothing
        // accumulates outside the retention window.
        val oldActive = keys.getAllActive().filter { it.keyVersion != nextVersion }
        if (oldActive.isNotEmpty()) {
            for (key in oldActive) {
                retireKey(key.keyVersion)
            }
            // An existing key was replaced: the server must be told about the
            // new public key and version before the old key is purged.
            events.emit(DeviceKeyRotatedEvent())
        }

        val saved = entity.copy(id = id)
        try {
            cleanupOldKeysLocked()
        } catch (e: Exception) {
            logsSvc.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Failed to clean up old keys",
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
    private suspend fun getCurrentKey(): DeviceKey? = withContext(Dispatchers.IO) {
        keys.getCurrent()
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

            val record = keys.getByKeyVersion(keyVersion) ?: return@withLock null
            val loaded = keyStore.getPrivateKey(keyAlias(record.keyVersion))
                ?: return@withLock null
            loaded.also { privateKeyCache[keyVersion] = it }
        }
    }

    /**
     * Returns the out-of-band verification fingerprint of the current active
     * public key, or null when no key exists or the stored key cannot be
     * decoded.
     */
    suspend fun getPublicKeyFingerprint(): String? = withContext(Dispatchers.IO) {
        val key = keys.getCurrent() ?: return@withContext null
        val der = try {
            Base64.decode(key.publicKeyBase64, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return@withContext null
        }
        Fingerprint.format(der)
    }

    /**
     * Retires the key with the given [keyVersion]: marks the Room row retired
     * only. The AndroidKeyStore entry is intentionally left intact so
     * messages encrypted to this version remain decryptable; it is removed
     * later by [cleanupOldKeys] after the 7-day retention cutoff.
     */
    private suspend fun retireKey(keyVersion: Int) {
        keys.retire(keyVersion)
        // The key is no longer active; drop any cached material for it.
        privateKeyCache.remove(keyVersion)
    }

    /**
     * Deletes keys that are retired and older than 7 days: removes the
     * AndroidKeyStore entry and the Room row in the same pass. When the
     * keystore deletion fails, the Room row is kept so the entry is retried
     * on the next pass instead of being orphaned with no state to clean up.
     *
     * Runs under [rotationMutex] so the removal of persistent state and the
     * cache invalidation are serialized with [getPrivateKey]'s load-and-cache
     * path: a purged key version can never be re-inserted into the cache after
     * its Room row and keystore entry are gone.
     */
    internal suspend fun cleanupOldKeys() {
        rotationMutex.withLock {
            cleanupOldKeysLocked()
        }
    }

    private suspend fun cleanupOldKeysLocked() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val expired = keys.getRetiredOlderThan(cutoff)
        if (expired.isEmpty()) return@withContext

        for (key in expired) {
            try {
                keyStore.delete(keyAlias(key.keyVersion))
                // Keystore entry confirmed gone: drop the Room row. If the
                // row delete fails, it is retried on the next pass (deleting
                // the now-missing keystore alias is a no-op).
                keys.deleteByKeyVersion(key.keyVersion)
            } catch (e: Exception) {
                // Keep the Room row so a failed keystore deletion is
                // retried later instead of orphaning the entry.
                logsSvc.insert(
                    LogEntry.Priority.WARN,
                    MODULE_NAME,
                    "Failed to delete keystore entry for key ${key.keyVersion}",
                    e,
                )
            }
            // Retired and past retention: drop the cached private key so it
            // is not retained in memory even if the keystore entry could not
            // be removed yet.
            privateKeyCache.remove(key.keyVersion)
        }
    }

    private fun keyAlias(version: Int): String = "e2e_key_v$version"

    companion object {
        private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
