package me.capcom.smsgateway.encryption

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Performance benchmarks + cross-language format verification for the E2E
 * hybrid scheme (RSA-2048 OAEP-SHA256 + AES-256-GCM), plan
 * `e2e-encryption-device-paired`.
 *
 * Acceptance thresholds (docs/plan/e2e-encryption/benchmarks.md):
 *  - API 21-22 (software keygen path): keygen < 5 s, per-message decrypt < 200 ms,
 *    100 messages < 15 s
 *  - API 28+ (AndroidKeyStore path): keygen < 1 s, per-message decrypt < 50 ms
 *  - API 23-27 runs the API 28+ thresholds (Keystore is available from API 23)
 *
 * Manual run:
 *  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.capcom.smsgateway.encryption.E2EEncryptionBenchmarkTest
 * on an API 21/22 and an API 28+ emulator.
 */
@RunWith(AndroidJUnit4::class)
class E2EEncryptionBenchmarkTest {

    private val tag = "E2EBenchmark"

    // ---------------------------------------------------------------------
    // Cross-language format check: decrypt the shared vector sample with the
    // exact JCA path used by EncryptionService.decryptE2E.
    // ---------------------------------------------------------------------

    @Test
    fun vectorSampleDecryptsWithAndroidJcaPath() {
        val vector = loadVector()
        val privateKey = parseVectorPrivateKey(vector)
        val plaintext = decryptE2EValue(vector.optString("fullFormatSample"), privateKey)

        assertEquals(vector.optString("plaintext"), plaintext)
        Log.i(tag, "vector fullFormatSample decrypted via Android JCA path OK")
    }

    // ---------------------------------------------------------------------
    // Key generation
    // ---------------------------------------------------------------------

    @Test
    fun keygenWithinThreshold() {
        val sdk = Build.VERSION.SDK_INT
        val limitMs = if (sdk < Build.VERSION_CODES.M) 5000L else 1000L

        val started = System.nanoTime()
        val keyPair = generateKeyPair(sdk)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue(
            "keygen took ${elapsedMs}ms, limit ${limitMs}ms (API $sdk)",
            elapsedMs < limitMs,
        )
        Log.i(tag, "keygen API=$sdk elapsed=${elapsedMs}ms")
    }

    // ---------------------------------------------------------------------
    // Per-message decryption
    // ---------------------------------------------------------------------

    @Test
    fun decryptWithinThreshold() {
        val sdk = Build.VERSION.SDK_INT
        val limitMs = if (sdk < Build.VERSION_CODES.M) 200L else 50L

        val keyPair = generateKeyPair(sdk)
        val value = encryptE2EValue(keyPair.public, "per-message benchmark payload")

        val started = System.nanoTime()
        val plaintext = decryptE2EValue(value, keyPair.private)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals("per-message benchmark payload", plaintext)
        assertTrue(
            "decrypt took ${elapsedMs}ms, limit ${limitMs}ms (API $sdk)",
            elapsedMs < limitMs,
        )
        Log.i(tag, "decrypt API=$sdk elapsed=${elapsedMs}ms")
    }

    // ---------------------------------------------------------------------
    // Batch: 100 messages
    // ---------------------------------------------------------------------

    @Test
    fun batch100DecryptsWithin15s() {
        val sdk = Build.VERSION.SDK_INT
        val keyPair = generateKeyPair(sdk)
        val values = (0 until 100).map {
            encryptE2EValue(keyPair.public, "batch message number $it")
        }

        val started = System.nanoTime()
        for (value in values) {
            decryptE2EValue(value, keyPair.private)
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue(
            "100 decryptions took ${elapsedMs}ms, limit 15000ms (API $sdk)",
            elapsedMs < 15_000L,
        )
        Log.i(tag, "batch100 API=$sdk elapsed=${elapsedMs}ms per-msg=${elapsedMs / 100.0}ms")
    }

    // ---------------------------------------------------------------------
    // Helpers (mirror E2EKeyService / EncryptionService)
    // ---------------------------------------------------------------------

    private fun generateKeyPair(sdk: Int): KeyPair {
        return if (sdk >= Build.VERSION_CODES.M) {
            val alias = "e2e_benchmark_${System.nanoTime()}"
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore",
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
            generator.initialize(spec)
            generator.generateKeyPair()
        } else {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            generator.generateKeyPair()
        }
    }

    private fun encryptE2EValue(publicKey: PublicKey, plaintext: String): String {
        val aesKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }

        val rsaCipher = Cipher.getInstance(RSA_OAEP_ALGORITHM)
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec())
        val encryptedAesKey = rsaCipher.doFinal(aesKey)

        val aesCipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        aesCipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        val ciphertext = aesCipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return "$E2E_FORMAT\$v=1\$k=1\$" +
            Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP) + "$" +
            Base64.encodeToString(iv, Base64.NO_WRAP) + "$" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decryptE2EValue(value: String, privateKey: PrivateKey): String {
        val chunks = value.split("$")
        check(chunks.size >= 7) { "Invalid E2E encrypted data format: expected at least 7 chunks" }
        check(chunks[2].removePrefix("v=") == "1") { "Unsupported E2E version: ${chunks[2]}" }
        chunks[3].removePrefix("k=").toIntOrNull()
            ?: error("Invalid E2E key version: ${chunks[3]}")

        val encryptedAesKey = Base64.decode(chunks[4], Base64.DEFAULT)
        val iv = Base64.decode(chunks[5], Base64.DEFAULT)
        val ciphertext = Base64.decode(chunks[6], Base64.DEFAULT)

        val rsaCipher = Cipher.getInstance(RSA_OAEP_ALGORITHM)
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec())
        val aesKey = rsaCipher.doFinal(encryptedAesKey)

        val aesCipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        aesCipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return String(aesCipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun oaepSpec(): OAEPParameterSpec {
        return OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT,
        )
    }

    private fun loadVector(): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = context.assets.open("e2e-vector-v1.json").bufferedReader().use { it.readText() }
        return JSONObject(json)
    }

    private fun parseVectorPrivateKey(vector: JSONObject): PrivateKey {
        val pem = vector.optString("privateKeyPem")
        val base64Body = pem
            .removePrefix("-----BEGIN PRIVATE KEY-----")
            .removeSuffix("-----END PRIVATE KEY-----")
            .replace("\n", "")
        val keyBytes = Base64.decode(base64Body, Base64.DEFAULT)
        val spec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
        return java.security.KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    companion object {
        private const val E2E_FORMAT = "\$rsa-oaep-aes-256-gcm"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val RSA_OAEP_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    }
}
