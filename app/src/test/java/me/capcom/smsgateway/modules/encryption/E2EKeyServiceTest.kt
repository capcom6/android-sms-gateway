package me.capcom.smsgateway.modules.encryption

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.modules.encryption.db.EncryptionKey
import me.capcom.smsgateway.modules.encryption.db.EncryptionKeysDao
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.LogsSettings
import me.capcom.smsgateway.modules.logs.db.LogEntriesDao
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.lang.reflect.Type
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class E2EKeyServiceTest {

    /** Real wall clock: the service no longer accepts an injectable nowMillis. */
    private val now = System.currentTimeMillis()
    private val dayMs = 24L * 60 * 60 * 1000

    // region fakes

    private class FakeEncryptionKeysDao : EncryptionKeysDao {
        val rows = mutableListOf<EncryptionKey>()
        val events = mutableListOf<String>()
        private var idSeq = 1L
        var throwOnInsert = false
        var getByKeyVersionCount = 0

        override suspend fun insert(encryptionKey: EncryptionKey): Long {
            events += "insert:${encryptionKey.keyVersion}"
            if (throwOnInsert) throw RuntimeException("insert failed")
            val id = idSeq++
            rows += encryptionKey.copy(id = id)
            return id
        }

        override suspend fun getCurrent(): EncryptionKey? =
            rows.filter { it.retiredAt == null }.maxByOrNull { it.id }

        override suspend fun getAllActive(): List<EncryptionKey> =
            rows.filter { it.retiredAt == null }.sortedByDescending { it.id }

        override suspend fun getAll(): List<EncryptionKey> = rows.sortedByDescending { it.id }

        override suspend fun getByKeyVersion(keyVersion: Int): EncryptionKey? {
            getByKeyVersionCount++
            return rows.firstOrNull { it.keyVersion == keyVersion }
        }

        override suspend fun retire(keyVersion: Int, retiredAt: Long) {
            events += "retire:$keyVersion"
            val idx = rows.indexOfFirst { it.keyVersion == keyVersion }
            if (idx >= 0) rows[idx] = rows[idx].copy(retiredAt = retiredAt)
        }

        override suspend fun getRetiredOlderThan(cutoffTime: Long): List<EncryptionKey> =
            rows.filter { it.retiredAt?.let { r -> r < cutoffTime } == true }

        override suspend fun deleteOld(cutoffTime: Long) {
            rows.removeAll { it.retiredAt?.let { r -> r < cutoffTime } == true }
        }

        override suspend fun countActive(): Int = rows.count { it.retiredAt == null }
    }

    private class FakeEncryptionKeyStore : EncryptionKeyStore {
        val keys = mutableMapOf<String, KeyPair>()
        val deleted = mutableListOf<String>()
        /** Counts keystore loads per alias (keystore IPC is the expensive part). */
        val loadCounts = mutableMapOf<String, Int>()

        override fun generateKeyPair(alias: String): KeyPairResult {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val keyPair = kpg.generateKeyPair()
            keys[alias] = keyPair
            return KeyPairResult(
                keyPair,
                ByteArray(0),
                Base64.getEncoder().encodeToString(keyPair.public.encoded)
            )
        }

        override fun getPrivateKey(alias: String, persistedBlob: ByteArray): KeyLoadResult? {
            loadCounts[alias] = (loadCounts[alias] ?: 0) + 1
            return keys[alias]?.let { KeyLoadResult(it.private) }
        }

        override fun delete(alias: String) {
            keys.remove(alias)
            deleted += alias
        }
    }

    private class FakeLogEntriesDao : LogEntriesDao {
        val inserted = mutableListOf<LogEntry>()

        override suspend fun selectByPeriod(from: Long, to: Long): List<LogEntry> = emptyList()
        override fun selectLast(): androidx.lifecycle.LiveData<List<LogEntry>> =
            TODO("not used by LogsService in these tests")

        override fun insert(entry: LogEntry) {
            inserted += entry
        }

        override suspend fun truncate(until: Long) {}
    }

    private class FakeKeyValueStorage : KeyValueStorage {
        val values = mutableMapOf<String, Any?>()

        override fun <T> set(key: String, value: T) {
            values[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, typeOfT: Type): T? = values[key] as T?

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    // endregion

    private fun createService(
        dao: FakeEncryptionKeysDao = FakeEncryptionKeysDao(),
        store: FakeEncryptionKeyStore = FakeEncryptionKeyStore(),
    ): E2EKeyService {
        val storage = FakeKeyValueStorage()
        return E2EKeyService(
            keyStore = store,
            dao = dao,
            logsSvc = LogsService(FakeLogEntriesDao(), LogsSettings(storage)),
            settings = EncryptionSettings(storage),
        )
    }

    private fun alias(version: Int): String = "e2e_key_v$version"

    /**
     * Builds an E2E ciphertext exactly in the production format:
     * $rsa-oaep-aes-256-gcm$v=1$k={keyVersion}$b64(aesKeyRsaOaep)$b64(iv)$b64(ct+tag)
     * Mirrors EncryptionService.decryptE2E with a JVM Base64.
     */
    private fun buildE2ECiphertext(
        keyVersion: Int,
        publicKey: PublicKey,
        plaintext: String,
    ): String {
        val aesKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
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
        val encryptedAesKey = rsaCipher.doFinal(aesKey)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = aesCipher.doFinal(plaintext.toByteArray())
        return listOf(
            "",
            "rsa-oaep-aes-256-gcm",
            "v=1",
            "k=$keyVersion",
            Base64.getEncoder().encodeToString(encryptedAesKey),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(ciphertext),
        ).joinToString("$")
    }

    /** Mirrors the decryption steps of EncryptionService.decryptE2E. */
    private fun decryptWithPrivateKey(privateKey: PrivateKey, ciphertext: String): String {
        val chunks = ciphertext.split('$')
        val encryptedAesKey = Base64.getDecoder().decode(chunks[4])
        val iv = Base64.getDecoder().decode(chunks[5])
        val ciphertextBytes = Base64.getDecoder().decode(chunks[6])
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
        val aesKey = rsaCipher.doFinal(encryptedAesKey)
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        return String(aesCipher.doFinal(ciphertextBytes))
    }

    // region tests

    @Test
    fun rotateKey_insertsNewKeyBeforeRetiringOld() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val service = createService(dao)

        service.rotateKey()
        service.rotateKey()
        service.rotateKey()

        // insert of the new version always precedes retire of the previous one:
        // no window where no active key exists
        assertEquals(
            listOf("insert:1", "insert:2", "retire:1", "insert:3", "retire:2"),
            dao.events,
        )
        assertEquals(3, dao.getCurrent()?.keyVersion)
        assertNull(dao.getByKeyVersion(3)?.retiredAt)
        assertNotNull(dao.getByKeyVersion(1)?.retiredAt)
        assertNotNull(dao.getByKeyVersion(2)?.retiredAt)
    }

    @Test
    fun rotateKey_marksOldKeyRetiredWithoutDeletingKeystoreEntry() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        service.rotateKey()
        service.rotateKey()

        assertEquals(2, dao.getCurrent()?.keyVersion)
        assertNotNull(dao.getByKeyVersion(1)?.retiredAt)
        // retireKey must NOT touch the AndroidKeyStore
        assertTrue(store.deleted.isEmpty())
        assertNotNull(store.keys[alias(1)])
        assertNotNull(store.keys[alias(2)])
    }

    @Test
    fun oldVersionKeyStillDecryptsAfterRotation() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        service.rotateKey()
        val v1Public = store.keys.getValue(alias(1)).public
        val ciphertext = buildE2ECiphertext(1, v1Public, "in-flight message to old key")

        service.rotateKey()

        val privateKey = service.getPrivateKey(1)
        assertNotNull(privateKey)
        assertEquals("in-flight message to old key", decryptWithPrivateKey(privateKey!!, ciphertext))
    }

    @Test
    fun cleanupOldKeys_purgesOnlyRetiredKeysOlderThan7Days() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        dao.rows += listOf(
            EncryptionKey(
                keyVersion = 1,
                privateKeyBlob = ByteArray(0),
                publicKeyBase64 = "k1",
                createdAt = now - 30L * dayMs,
                retiredAt = now - 8L * dayMs,
            ),
            EncryptionKey(
                keyVersion = 2,
                privateKeyBlob = ByteArray(0),
                publicKeyBase64 = "k2",
                createdAt = now - 30L * dayMs,
                retiredAt = now - dayMs,
            ),
            // boundary: retired just inside the 7-day window. The service uses
            // the real wall clock (no injectable nowMillis), so a small clock-
            // drift buffer keeps this row inside the retention window: retained.
            EncryptionKey(
                keyVersion = 3,
                privateKeyBlob = ByteArray(0),
                publicKeyBase64 = "k3",
                createdAt = now - 30L * dayMs,
                retiredAt = now - 7L * dayMs + 120_000L,
            ),
            EncryptionKey(
                keyVersion = 4,
                privateKeyBlob = ByteArray(0),
                publicKeyBase64 = "k4",
                createdAt = now - dayMs,
                retiredAt = null,
            ),
        )
        for (v in 1..4) {
            store.generateKeyPair(alias(v))
        }

        service.cleanupOldKeys()

        assertNull(dao.getByKeyVersion(1))
        assertNotNull(dao.getByKeyVersion(2))
        assertNotNull(dao.getByKeyVersion(3))
        assertNotNull(dao.getByKeyVersion(4))
        // keystore entries purged in the same pass as the Room rows
        assertEquals(listOf(alias(1)), store.deleted)
        assertNull(store.keys[alias(1)])
        assertNotNull(store.keys[alias(2)])
        assertNotNull(store.keys[alias(3)])
    }

    @Test
    fun enforceKeyLimit_retiresOldestButKeepsDecryptability() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        for (v in 1..4) {
            store.generateKeyPair(alias(v))
            dao.rows += EncryptionKey(
                id = v.toLong(),
                keyVersion = v,
                privateKeyBlob = ByteArray(0),
                publicKeyBase64 = "k$v",
                createdAt = now - (5L - v) * dayMs,
            )
        }

        service.enforceKeyLimit()

        assertEquals(listOf(2, 3, 4), dao.getAllActive().map { it.keyVersion }.sorted())
        assertNotNull(dao.getByKeyVersion(1)?.retiredAt)
        // limit enforcement retires the Room row only; decryptability intact
        assertTrue(store.deleted.isEmpty())
        assertNotNull(service.getPrivateKey(1))
    }

    @Test
    fun rotateKey_deletesOrphanedKeystoreEntryWhenInsertFails() = runBlocking {
        val dao = FakeEncryptionKeysDao().apply { throwOnInsert = true }
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        try {
            service.rotateKey()
            fail("expected RuntimeException from dao.insert")
        } catch (e: RuntimeException) {
            assertEquals("insert failed", e.message)
        }

        assertEquals(listOf(alias(1)), store.deleted)
        assertNull(store.keys[alias(1)])
    }

    @Test
    fun ensureKey_createsKeyWhenMissingAndReturnsExisting() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val service = createService(dao)

        val created = service.ensureKey()
        assertEquals(1, created?.keyVersion)

        val existing = service.ensureKey()
        assertEquals(1, existing?.keyVersion)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun getPrivateKey_doesNotRewriteLegacyBlob() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        store.generateKeyPair(alias(1))
        dao.rows += EncryptionKey(
            id = 1,
            keyVersion = 1,
            privateKeyBlob = byteArrayOf(1, 2, 3), // legacy blob, pre-upgrade
            publicKeyBase64 = "k1",
        )

        val key = service.getPrivateKey(1)

        // Current API: KeyLoadResult carries the private key only; the service
        // never re-encrypts or persists blob upgrades (that lives in
        // StorageBlobCipher). The stored blob must stay byte-identical.
        assertNotNull(key)
        assertTrue(dao.rows.first { it.keyVersion == 1 }.privateKeyBlob.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(dao.events.isEmpty())
    }

    @Test
    fun getPrivateKey_doesNotRewriteCurrentFormatBlob() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        store.generateKeyPair(alias(1))
        dao.rows += EncryptionKey(
            id = 1,
            keyVersion = 1,
            privateKeyBlob = ByteArray(0), // current-format blob: no re-encryption
            publicKeyBase64 = "k1",
        )

        val key = service.getPrivateKey(1)

        assertNotNull(key)
        assertTrue(dao.events.isEmpty())
    }

    @Test
    fun getPrivateKey_cachesLoadedKeyPerVersion() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        service.rotateKey()

        for (i in 1..5) {
            assertNotNull(service.getPrivateKey(1))
        }

        // N repeated loads of the same keyVersion: exactly 1 Room lookup + 1 keystore load
        assertEquals(1, dao.getByKeyVersionCount)
        assertEquals(1, store.loadCounts[alias(1)])
    }

    @Test
    fun getPrivateKey_batchLoadsOnceConcurrently() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        service.rotateKey()

        val keys = (1..8).map {
            async(Dispatchers.Default) { service.getPrivateKey(1) }
        }.awaitAll()

        assertTrue(keys.all { it != null })
        // A batch of N values with the same keyVersion: exactly 1 Room lookup + 1 keystore load
        assertEquals(1, dao.getByKeyVersionCount)
        assertEquals(1, store.loadCounts[alias(1)])
    }

    @Test
    fun getPrivateKey_reloadsAfterRotation_clearsCache() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        service.rotateKey()
        assertNotNull(service.getPrivateKey(1)) // loads + caches v1

        service.rotateKey() // retires v1, must invalidate the cache

        assertNotNull(service.getPrivateKey(1))
        val v1KeystoreLoads = store.loadCounts[alias(1)] ?: 0
        assertEquals("rotation must invalidate the in-memory cache", 2, v1KeystoreLoads)
    }

    @Test
    fun cleanupOldKeys_doesNotServeStaleCachedKey() = runBlocking {
        val dao = FakeEncryptionKeysDao()
        val store = FakeEncryptionKeyStore()
        val service = createService(dao, store)

        dao.rows += EncryptionKey(
            keyVersion = 1,
            privateKeyBlob = ByteArray(0),
            publicKeyBase64 = "k1",
            createdAt = now - 30L * dayMs,
            retiredAt = now - 8L * dayMs,
        )
        store.generateKeyPair(alias(1))

        assertNotNull(service.getPrivateKey(1)) // loads + caches v1
        service.cleanupOldKeys() // purges v1: cache must be invalidated too

        // A stale cache would return the purged key; the reload must miss in Room.
        assertNull(service.getPrivateKey(1))
        assertEquals(
            "purged version must be re-resolved from Room, not served from cache",
            2,
            dao.getByKeyVersionCount,
        )
    }

    // endregion

    private companion object {
        const val RSA_OAEP_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    }
}
