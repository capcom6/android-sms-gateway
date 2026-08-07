package me.capcom.smsgateway.modules.encryption

import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.modules.encryption.providers.PassphraseEncryptionProvider
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Type
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM tests for PassphraseDecryptor. Ciphertext is built in-test with the
 * same JCA recipe (PBKDF2WithHmacSHA1 + AES/CBC/PKCS5Padding) using the
 * JVM java.util.Base64; production keeps android.util.Base64 (minSdk 21).
 */
class PassphraseEncryptionProviderTest {

    private class FakeStorage : KeyValueStorage {
        private val values = mutableMapOf<String, Any?>()

        override fun <T> set(key: String, value: T) {
            values[key] = value
        }

        override fun <T> get(key: String, typeOfT: Type): T? = values[key] as T?

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private fun settingsWithPassphrase(passphrase: String): EncryptionSettings =
        EncryptionSettings(FakeStorage().apply { set("passphrase", passphrase) })

    private fun decryptor(passphrase: String): PassphraseEncryptionProvider =
        PassphraseEncryptionProvider(settingsWithPassphrase(passphrase))

    /**
     * Mirrors the production payload format:
     * $aes-256-cbc/pbkdf2-sha1$i={iterations}$b64(salt)$b64(ciphertext)
     * The salt doubles as the AES-CBC IV, exactly like the production decryptor.
     */
    private fun encrypt(
        passphrase: String,
        plaintext: String,
        iterationCount: Int = 1_000,
    ): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, iterationCount, 256)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val key = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(salt))
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return listOf(
            "",
            PassphraseEncryptionProvider.PASSPHRASE_FORMAT,
            "i=$iterationCount",
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(ciphertext),
        ).joinToString("$")
    }

    // region happy path + input variation

    @Test
    fun decrypt_validPayload_roundTrips() = runBlocking {
        val payload = encrypt("correct horse battery staple", "hello world")
        assertEquals("hello world", decryptor("correct horse battery staple").decrypt(payload))
    }

    @Test
    fun decrypt_varyingPlaintexts_roundTrips() = runBlocking {
        val passphrase = "p@ssw0rd"
        val d = decryptor(passphrase)
        val plaintexts = listOf(
            "x",
            "a".repeat(5000),
            "unicode \u20ac \u4e2d\u6587 \uD83D\uDE00",
        )
        for (plaintext in plaintexts) {
            assertEquals(plaintext, d.decrypt(encrypt(passphrase, plaintext)))
        }
    }

    @Test
    fun decrypt_productionIterationCount_roundTrips() = runBlocking {
        val passphrase = "production-compat"
        val payload = encrypt(passphrase, "secret", iterationCount = 300_000)
        assertEquals("secret", decryptor(passphrase).decrypt(payload))
    }

    // endregion

    // region error paths

    @Test
    fun decrypt_missingIterationParam_throws() = runBlocking {
        val salt = Base64.getEncoder().encodeToString(ByteArray(16))
        val payload =
            listOf("", PassphraseEncryptionProvider.PASSPHRASE_FORMAT, "k=1", salt, "dGV4dA==")
                .joinToString("$")

        try {
            decryptor("p").decrypt(payload)
            fail("expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("Missing iteration count", e.message)
        }
    }

    @Test
    fun decrypt_shortChunks_throws() = runBlocking {
        val d = decryptor("p")
        val payloads = listOf(
            "no-chunks",
            "\$aes-256-cbc/pbkdf2-sha1",
            "\$aes-256-cbc/pbkdf2-sha1\$i=1000",
            "\$aes-256-cbc/pbkdf2-sha1\$i=1000\$c2FsdA==",
        )
        for (payload in payloads) {
            try {
                d.decrypt(payload)
                fail("expected RuntimeException")
            } catch (e: RuntimeException) {
                assertEquals("Invalid passphrase encrypted data format", e.message)
            }
        }
    }

    @Test
    fun decrypt_missingPassphrase_throws() = runBlocking {
        val settings = EncryptionSettings(FakeStorage())
        val d = PassphraseEncryptionProvider(settings)
        val payload = encrypt("irrelevant", "hello")

        try {
            d.decrypt(payload)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Passphrase is not set", e.message)
        }
    }

    @Test
    fun decrypt_wrongPassphrase_failsDecryption() = runBlocking {
        val payload = encrypt("correct", "hello")

        try {
            decryptor("wrong").decrypt(payload)
            fail("expected GeneralSecurityException")
        } catch (e: GeneralSecurityException) {
            // expected: PBKDF2 derives a different key, CBC padding check fails
        }
    }

    // endregion

    @Test
    fun algorithmId_isPassphraseFormat() {
        assertEquals("aes-256-cbc/pbkdf2-sha1", decryptor("p").algorithmId)
    }
}
