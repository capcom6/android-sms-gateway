package me.capcom.smsgateway.modules.encryption

import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.modules.encryption.db.EncryptionKey
import me.capcom.smsgateway.modules.encryption.db.EncryptionKeysDao
import me.capcom.smsgateway.modules.encryption.providers.PassphraseEncryptionProvider
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
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Deterministic facade tests for EncryptionService. The facade is built from
 * real decryptors wired with fake dependencies; payloads are constructed
 * in-test with the same JCA recipes as PassphraseDecryptorTest/E2EDecryptorTest.
 */
class EncryptionServiceTest {

    // region fakes (mirror PassphraseDecryptorTest / E2EDecryptorTest)

    private class FakeKeyValueStorage : KeyValueStorage {
        private val values = mutableMapOf<String, Any?>()

        override fun <T> set(key: String, value: T) {
            values[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, typeOfT: Type): T? = values[key] as T?

        override fun remove(key: String) {
            values.remove(key)
        }
    }

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

    // endregion

    private fun passphraseSettings(passphrase: String): EncryptionSettings =
        EncryptionSettings(FakeKeyValueStorage().apply { set("passphrase", passphrase) })

    private fun e2eKeyService(
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

    private fun service(): EncryptionService =
        EncryptionService(
            passphraseEncryptionProvider = PassphraseEncryptionProvider(passphraseSettings("test-passphrase")),
            RSAEncryptionProvider = RSAEncryptionProvider(e2eKeyService()),
        )

    /**
     * Mirrors the production passphrase payload format:
     * $aes-256-cbc/pbkdf2-sha1$i=1000$b64(salt)$b64(ciphertext)
     */
    private fun buildPassphrasePayload(passphrase: String, plaintext: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, 1_000, 256)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val key = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(salt))
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return listOf(
            "",
            PassphraseEncryptionProvider.PASSPHRASE_FORMAT,
            "i=1000",
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(ciphertext),
        ).joinToString("$")
    }

    /**
     * Mirrors the production E2E payload format:
     * $rsa-oaep-aes-256-gcm$v=1$k=1$b64(rsaEncAesKey)$b64(iv)$b64(ct+tag)
     */
    private fun buildE2EPayload(publicKey: PublicKey, plaintext: String): String {
        val aesKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
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
            RSAEncryptionProvider.E2E_FORMAT,
            "v=1",
            "k=1",
            Base64.getEncoder().encodeToString(encryptedAesKey),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(ciphertext),
        ).joinToString("$")
    }

    // region error paths

    @Test
    fun decrypt_lessThanThreeChunks_throwsExactMessage() = runBlocking {
        val payloads = listOf(
            "",
            "single",
            "two\$chunks",
        )
        for (payload in payloads) {
            try {
                service().decrypt(payload)
                fail("expected RuntimeException for: $payload")
            } catch (e: RuntimeException) {
                assertEquals("Invalid encrypted data format", e.message)
            }
        }
    }

    @Test
    fun decrypt_unknownAlgorithm_throwsExactMessage() = runBlocking {
        val payloads = listOf(
            "\$unknown-algo\$a\$b",
            "\$aes-128-cbc/pbkdf2-sha1\$a\$b",
            "\$rot13\$a\$b",
        )
        val expectedMessages = listOf(
            "Unsupported algorithm: unknown-algo",
            "Unsupported algorithm: aes-128-cbc/pbkdf2-sha1",
            "Unsupported algorithm: rot13",
        )
        for ((payload, expected) in payloads.zip(expectedMessages)) {
            try {
                service().decrypt(payload)
                fail("expected RuntimeException for: $payload")
            } catch (e: RuntimeException) {
                assertEquals(expected, e.message)
            }
        }
    }

    // endregion

    // region happy path + routing

    @Test
    fun decrypt_validPassphrasePayload_roundTrips() = runBlocking {
        val passphrase = "correct horse battery staple"
        val facade = EncryptionService(
            passphraseEncryptionProvider = PassphraseEncryptionProvider(
                passphraseSettings(
                    passphrase
                )
            ),
            RSAEncryptionProvider = RSAEncryptionProvider(e2eKeyService()),
        )

        val plaintexts = listOf(
            "hello world",
            "x",
            "a".repeat(5000),
            "unicode \u20ac \u4e2d\u6587 \uD83D\uDE00",
        )
        for (plaintext in plaintexts) {
            assertEquals(plaintext, facade.decrypt(buildPassphrasePayload(passphrase, plaintext)))
        }
    }

    @Test
    fun decrypt_validE2EPayload_routesToE2EDecryptor() = runBlocking {
        val store = FakeEncryptionKeyStore()
        val keyService = e2eKeyService(store = store)
        keyService.rotateKey()
        val publicKey = store.keys.getValue("e2e_key_v1").public
        val facade = EncryptionService(
            passphraseEncryptionProvider = PassphraseEncryptionProvider(passphraseSettings("test-passphrase")),
            RSAEncryptionProvider = RSAEncryptionProvider(keyService),
        )

        assertEquals(
            "top-secret message",
            facade.decrypt(buildE2EPayload(publicKey, "top-secret message"))
        )
    }

    @Test
    fun decrypt_passphraseFormattedPayload_dispatchedToPassphraseDecryptor() = runBlocking {
        // 3 chunks pass the facade size check; the forwarded decryptor must
        // reject the truncated payload with ITS error, proving the dispatch.
        try {
            service().decrypt("\$aes-256-cbc/pbkdf2-sha1\$a\$b")
            fail("expected RuntimeException from passphrase decryptor")
        } catch (e: RuntimeException) {
            assertEquals("Invalid passphrase encrypted data format", e.message)
        }
    }

    @Test
    fun decrypt_e2eFormattedPayload_dispatchedToE2EDecryptor() = runBlocking {
        try {
            service().decrypt("\$rsa-oaep-aes-256-gcm\$a\$b")
            fail("expected RuntimeException from E2E decryptor")
        } catch (e: RuntimeException) {
            assertEquals("Invalid E2E encrypted data format: expected at least 7 chunks", e.message)
        }
    }

    // endregion
}
