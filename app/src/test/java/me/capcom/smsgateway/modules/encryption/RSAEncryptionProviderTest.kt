package me.capcom.smsgateway.modules.encryption

import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.modules.encryption.db.EncryptionKey
import me.capcom.smsgateway.modules.encryption.db.EncryptionKeysDao
import me.capcom.smsgateway.modules.encryption.providers.RSAEncryptionProvider
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.LogsSettings
import me.capcom.smsgateway.modules.logs.db.LogEntriesDao
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Type
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class RSAEncryptionProviderTest {

    // region fakes (mirror E2EKeyServiceTest)

    private class FakeEncryptionKeysDao : EncryptionKeysDao {
        val rows = mutableListOf<EncryptionKey>()
        private var idSeq = 1L

        override suspend fun insert(encryptionKey: EncryptionKey): Long {
            val id = idSeq++
            rows += encryptionKey.copy(id = id)
            return id
        }

        override suspend fun getCurrent(): EncryptionKey? =
            rows.filter { it.retiredAt == null }.maxByOrNull { it.id }

        override suspend fun getAllActive(): List<EncryptionKey> =
            rows.filter { it.retiredAt == null }.sortedByDescending { it.id }

        override suspend fun getAll(): List<EncryptionKey> = rows.sortedByDescending { it.id }

        override suspend fun getByKeyVersion(keyVersion: Int): EncryptionKey? =
            rows.firstOrNull { it.keyVersion == keyVersion }

        override suspend fun retire(keyVersion: Int, retiredAt: Long) {
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

        override fun getPrivateKey(alias: String, persistedBlob: ByteArray): KeyLoadResult? =
            keys[alias]?.let { KeyLoadResult(it.private) }

        override fun delete(alias: String) {
            keys.remove(alias)
        }
    }

    private class FakeLogEntriesDao : LogEntriesDao {
        override suspend fun selectByPeriod(from: Long, to: Long): List<LogEntry> = emptyList()
        override fun selectLast(): androidx.lifecycle.LiveData<List<LogEntry>> =
            TODO("not used by LogsService in these tests")

        override fun insert(entry: LogEntry) {
        }

        override suspend fun truncate(until: Long) {
        }
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
     * Mirrors E2EDecryptor.decrypt with a JVM Base64.
     */
    private fun buildE2ECiphertext(
        publicKey: PublicKey,
        plaintext: String,
        versionParam: String = "v=1",
        keyVersionParam: String = "k=1",
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
            versionParam,
            keyVersionParam,
            Base64.getEncoder().encodeToString(encryptedAesKey),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(ciphertext),
        ).joinToString("$")
    }

    // region tests

    @Test
    fun roundTrip_decryptsPayloadEncryptedToCurrentKeyVersion() = runBlocking {
        val store = FakeEncryptionKeyStore()
        val service = createService(store = store)
        val decryptor = RSAEncryptionProvider(service)

        service.rotateKey()
        val publicKey = store.keys.getValue(alias(1)).public
        val ciphertext = buildE2ECiphertext(publicKey, "top-secret message")

        assertEquals("top-secret message", decryptor.decrypt(ciphertext))
    }

    @Test
    fun unsupportedVersion_throwsExactMessage() = runBlocking {
        val store = FakeEncryptionKeyStore()
        val decryptor = RSAEncryptionProvider(createService(store = store))

        store.generateKeyPair(alias(1))
        val publicKey = store.keys.getValue(alias(1)).public
        val ciphertext = buildE2ECiphertext(publicKey, "irrelevant", versionParam = "v=2")

        try {
            decryptor.decrypt(ciphertext)
            fail("expected RuntimeException for unsupported version")
        } catch (e: RuntimeException) {
            assertEquals("Unsupported E2E version: 2", e.message)
        }
    }

    @Test
    fun invalidKeyVersion_throwsExactMessage() = runBlocking {
        val store = FakeEncryptionKeyStore()
        val decryptor = RSAEncryptionProvider(createService(store = store))

        store.generateKeyPair(alias(1))
        val publicKey = store.keys.getValue(alias(1)).public
        val ciphertext = buildE2ECiphertext(publicKey, "irrelevant", keyVersionParam = "k=abc")

        try {
            decryptor.decrypt(ciphertext)
            fail("expected RuntimeException for invalid key version")
        } catch (e: RuntimeException) {
            assertEquals("Invalid E2E key version: k=abc", e.message)
        }
    }

    @Test
    fun missingKeyVersion_throwsExactMessage() = runBlocking {
        // Store has a keypair for building the payload, but the DAO rows omit
        // that key version: getPrivateKey must miss and the decryptor must fail.
        val store = FakeEncryptionKeyStore()
        val dao = FakeEncryptionKeysDao()
        val decryptor = RSAEncryptionProvider(createService(dao = dao, store = store))

        store.generateKeyPair(alias(1))
        val publicKey = store.keys.getValue(alias(1)).public
        val ciphertext = buildE2ECiphertext(publicKey, "irrelevant", keyVersionParam = "k=1")

        try {
            decryptor.decrypt(ciphertext)
            fail("expected RuntimeException for missing key version")
        } catch (e: RuntimeException) {
            assertEquals("No E2E private key available for version 1", e.message)
        }
    }

    @Test
    fun fewerThanSevenChunks_throwsExactMessage() = runBlocking {
        val decryptor = RSAEncryptionProvider(createService())

        try {
            decryptor.decrypt("\$rsa-oaep-aes-256-gcm\$v=1\$k=1\$x")
            fail("expected RuntimeException for truncated payload")
        } catch (e: RuntimeException) {
            assertEquals("Invalid E2E encrypted data format: expected at least 7 chunks", e.message)
        }
    }

    // endregion

    private companion object {
        const val RSA_OAEP_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    }
}
