package me.capcom.smsgateway.modules.encryption

import android.util.Base64
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionServiceBatchTest {

    // Encrypted with OpenSSL (independent of any repo code path):
    // PBKDF2WithHmacSHA1(passphrase="legacy-fixture-pass", salt=00..0f, 1000 iters, 256-bit)
    // + AES/CBC/PKCS7 with iv = salt (legacy 5-part salt-as-IV layout).
    private val LEGACY_FIXTURE_1 =
        "\$aes-256-cbc/pbkdf2-sha1\$i=1000\$AAECAwQFBgcICQoLDA0ODw==\$cmrdBLX8/xV4YgbtlLt4g92+xLD8I8ik9vLczC/KDCE="

    // OpenSSL: passphrase="another-legacy-pass", salt=20..2f, 30000 iters.
    private val LEGACY_FIXTURE_2 =
        "\$aes-256-cbc/pbkdf2-sha1\$i=30000\$ICEiIyQlJicoKSorLC0uLw==\$7bUPmspcAv+Wh6YRNyYydvtmi4FW7Z6qIb3cGGfWn3U="

    private fun service(passphrase: String?): EncryptionService =
        EncryptionService(EncryptionSettings(FakeStorage(passphrase)))

    // One-field convenience over the batch API: a single call is its own batch
    // (one key derivation, one per-field IV), mirroring the old encryptField().
    private fun encryptField(encryptor: EncryptionService, plaintext: String, iterations: Int = 1000): String =
        encryptor.encryptBatch(listOf(plaintext), iterationCount = iterations).first()

    @Test
    fun roundtripManyPlaintexts() {
        val encryptor = service("roundtrip-pass")
        val decryptor = service("roundtrip-pass")
        val values = listOf(
            "Hello, SMS Gateway!",
            "x",
            " ",
            "",
            "Привет, мир! こんにちは世界 🚀",
            "line1\nline2\ttabbed",
            "1234567890".repeat(1000),
            "emoji and ascii mix !@#\$%^&*()"
        )
        for (value in values) {
            assertEquals("roundtrip failed for <$value>", value, decryptor.decrypt(encryptField(encryptor, value)))
        }
    }

    @Test
    fun samePlaintextEncryptedTwiceDiffers() {
        val encryptor = service("random-iv-pass")
        val first = encryptField(encryptor, "same value")
        val second = encryptField(encryptor, "same value")

        assertNotEquals(first, second)
        assertNotEquals(first.split('$')[4], second.split('$')[4])
        assertNotEquals(first.split('$')[5], second.split('$')[5])
    }

    @Test
    fun sixPartFormatShape() {
        val encrypted = encryptField(service("shape-pass"), "shape test")
        val chunks = encrypted.split('$')

        assertEquals(6, chunks.size)
        assertEquals("", chunks[0])
        assertEquals("aes-256-cbc/pbkdf2-sha1", chunks[1])
        assertEquals("i=1000", chunks[2])

        val salt = Base64.decode(chunks[3], Base64.NO_WRAP)
        val iv = Base64.decode(chunks[4], Base64.NO_WRAP)
        assertEquals(16, salt.size)
        assertEquals(16, iv.size)
        assertNotEquals(salt.toList(), iv.toList())
        assertTrue(Base64.decode(chunks[5], Base64.NO_WRAP).isNotEmpty())
    }

    @Test
    fun oneDerivationServesManyFields() {
        var derivations = 0
        val encryptor = service("single-derive-pass")
        val decryptor = service("single-derive-pass")
        val fields = listOf("sender", "recipient", "contentPreview", "attachment-name", "attachment-data")
        val encrypted = encryptor.encryptBatch(
            fields,
            iterationCount = 1000,
            keyFactory = { p, s, i ->
                derivations++
                deriveBatchKey(p, s, i)
            }
        )

        assertEquals(1, derivations)
        encrypted.forEachIndexed { index, value ->
            assertEquals(fields[index], decryptor.decrypt(value))
        }

        val salts = encrypted.map { it.split('$')[3] }
        assertEquals(1, salts.toSet().size)
        val ivs = encrypted.map { it.split('$')[4] }
        assertEquals(encrypted.size, ivs.toSet().size)
    }

    @Test
    fun legacyFivePartFixtureDecrypts() {
        assertEquals("legacy 5-part fixture message", service("legacy-fixture-pass").decrypt(LEGACY_FIXTURE_1))
        assertEquals("second legacy fixture", service("another-legacy-pass").decrypt(LEGACY_FIXTURE_2))
    }

    @Test
    fun legacyFivePartFormatShape() {
        val chunks = LEGACY_FIXTURE_1.split('$')

        assertEquals(5, chunks.size)
        assertEquals("", chunks[0])
        assertEquals("aes-256-cbc/pbkdf2-sha1", chunks[1])
        assertEquals("i=1000", chunks[2])
        assertEquals(16, Base64.decode(chunks[3], Base64.NO_WRAP).size)
        assertTrue(Base64.decode(chunks[4], Base64.NO_WRAP).isNotEmpty())
    }

    @Test
    fun legacyDynamicFixtureWithUnicodeRoundtrips() {
        // 5-part fixture assembled via raw JCE, independent of the encrypt code path.
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val plaintext = "legacy path unicode: 漢字 и кириллица"
        val key = deriveKeyJce("dynamic-legacy-pass", salt, 1000)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(salt))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val legacy = "\$aes-256-cbc/pbkdf2-sha1\$i=1000\$" +
            Base64.encodeToString(salt, Base64.NO_WRAP) + "\$" +
            Base64.encodeToString(ct, Base64.NO_WRAP)

        assertEquals(plaintext, service("dynamic-legacy-pass").decrypt(legacy))
    }

    @Test
    fun malformedFormatsThrow() {
        val decryptor = service("pass")
        val runtime = RuntimeException::class.java

        assertThrows(runtime) { decryptor.decrypt("\$aes-256-cbc/pbkdf2-sha1\$i=1000\$AA==") }
        assertThrows(runtime) { decryptor.decrypt(LEGACY_FIXTURE_1 + "\$extra\$extra2") }
        assertThrows(runtime) {
            decryptor.decrypt("\$blowfish-cbc\$i=1000\$AAECAwQFBgcICQoLDA0ODw==\$AA==\$AA==\$AA==")
        }
        assertThrows(runtime) { decryptor.decrypt("\$aes-256-cbc/pbkdf2-sha1\$x=1\$AAECAwQFBgcICQoLDA0ODw==\$AA==\$AA==") }
    }

    @Test
    fun wrongPassphraseNeverYieldsOriginalPlaintext() {
        val encryptor = service("correct-pass")
        val decryptor = service("wrong-pass")
        val plaintexts = listOf("secret content", "another secret", "third secret value")
        val ciphertexts = plaintexts.map { encryptField(encryptor, it) }

        var thrown = 0
        ciphertexts.forEachIndexed { index, ct ->
            val result = runCatching { decryptor.decrypt(ct) }
            if (result.isFailure) {
                thrown++
            } else {
                // CBC/PKCS5 with a wrong key may pass unpadding with ~1/256
                // probability; the crypto property that must hold is that the
                // original plaintext is never recovered.
                assertNotEquals("wrong key recovered original plaintext", plaintexts[index], result.getOrNull())
            }
        }
        // Probability that all three accidentally pass unpadding is ~(1/256)^3.
        assertTrue("expected at least one wrong-key decrypt to fail", thrown > 0)
    }

    @Test
    fun nullPassphraseRejectedByEncryptApi() {
        val e = assertThrows(IllegalArgumentException::class.java) { service(null).encryptBatch(listOf("x")) }
        assertEquals("Passphrase is not set", e.message)
    }

    @Test
    fun emptyPassphraseRoundtrips() {
        val encrypted = encryptField(service(""), "content with empty passphrase")
        assertEquals("content with empty passphrase", service("").decrypt(encrypted))
    }

    @Test
    fun defaultIterationCountMatchesPlanConstant() {
        assertEquals(300_000, EncryptionService.DEFAULT_ITERATION_COUNT)
        val encrypted = service("plan-default-pass").encryptBatch(listOf("default iters")).first()
        assertEquals("i=300000", encrypted.split('$')[2])
        assertEquals("default iters", service("plan-default-pass").decrypt(encrypted))
    }

    @Test
    fun explicitIterationCountEmittedInFormat() {
        val encrypted = encryptField(service("iter-pass"), "few iters", iterations = 1000)
        assertEquals("i=1000", encrypted.split('$')[2])
    }

    @Test
    fun saltRandomAcrossBatches() {
        val first = encryptField(service("rand-salt-pass"), "value")
        val second = encryptField(service("rand-salt-pass"), "value")
        assertNotEquals(first.split('$')[3], second.split('$')[3])
    }

    private fun deriveKeyJce(passphrase: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val bytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private class FakeStorage(private var passphrase: String?) : KeyValueStorage {
        private val values = mutableMapOf<String, Any?>()

        override fun <T> set(key: String, value: T) {
            values[key] = value
        }

        override fun <T> get(key: String, typeOfT: Type): T? {
            @Suppress("UNCHECKED_CAST")
            return (if (key == "passphrase") passphrase else values[key]) as T?
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }
}