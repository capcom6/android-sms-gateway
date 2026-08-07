package me.capcom.smsgateway.modules.encryption

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM tests for the software (API 21-22) E2E key blob codec: versioned blob
 * format, PBKDF2 KDF, legacy (SHA-256) compat + upgrade, tamper detection.
 */
class StorageBlobCipherTest {

    private val material = "some-android-id:me.capcom.smsgateway"

    private fun newCipher(material: String = this.material) = StorageBlobCipher(material)

    private fun privateKeyBytes(): ByteArray {
        val kpg = java.security.KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        return kpg.generateKeyPair().private.encoded
    }

    // ---------------------------------------------------------------------
    // helpers mirroring the documented blob layout
    // ---------------------------------------------------------------------

    private data class Header(
        val version: Int,
        val kdfId: Int,
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
    )

    private fun headerOf(blob: ByteArray): Header = Header(
        version = blob[4].toInt(),
        kdfId = blob[5].toInt(),
        iterations = readInt(blob, 6),
        salt = blob.copyOfRange(10, 26),
        iv = blob.copyOfRange(26, 38),
    )

    private fun readInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF shl 24) or
            (b[off + 1].toInt() and 0xFF shl 16) or
            (b[off + 2].toInt() and 0xFF shl 8) or
            (b[off + 3].toInt() and 0xFF)

    private fun writeInt(b: ByteArray, off: Int, v: Int) {
        b[off] = (v ushr 24).toByte()
        b[off + 1] = (v ushr 16).toByte()
        b[off + 2] = (v ushr 8).toByte()
        b[off + 3] = v.toByte()
    }

    private fun buildV1Blob(
        version: Int,
        kdfId: Int,
        iterations: Int,
        salt: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val out = ByteArray(38 + ciphertext.size)
        byteArrayOf(0x53, 0x4D, 0x53, 0x4B).copyInto(out, 0) // "SMSK"
        out[4] = version.toByte()
        out[5] = kdfId.toByte()
        writeInt(out, 6, iterations)
        salt.copyInto(out, 10)
        iv.copyInto(out, 26)
        ciphertext.copyInto(out, 38)
        return out
    }

    /** Mirrors the pre-upgrade production format: iv(12) || AES-GCM(sha256(material)). */
    private fun buildLegacyBlob(material: String, plaintext: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = SecretKeySpec(digest.digest(material.toByteArray()).copyOf(32), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        return iv + cipher.doFinal(plaintext)
    }

    // ---------------------------------------------------------------------
    // new-format blob (PBKDF2)
    // ---------------------------------------------------------------------

    @Test
    fun newBlob_hasPbkdf2HeaderWithSaltAndParams_roundTrips() {
        val plaintext = privateKeyBytes()
        val blob = newCipher().encrypt(plaintext)

        assertTrue(blob.size > 38)
        val header = headerOf(blob)
        assertEquals("magic must be present", "SMSK", String(blob.copyOfRange(0, 4)))
        assertEquals(1, header.version)
        assertEquals(StorageBlobCipher.KDF_ID_PBKDF2, header.kdfId)
        assertTrue("iterations >= 100k", header.iterations >= 100_000)
        assertEquals(16, header.salt.size)
        assertTrue(header.salt.any { it != 0.toByte() })
        assertEquals(12, header.iv.size)

        val result = newCipher().decrypt(blob)
        assertArrayEquals(plaintext, result.plaintext)
        assertNull("current-format blob must not yield an upgrade", result.upgradedBlob)
    }

    @Test
    fun newBlob_usesFreshRandomSaltPerEncryption() {
        val plaintext = privateKeyBytes()
        val cipher = newCipher()
        val a = cipher.encrypt(plaintext)
        val b = cipher.encrypt(plaintext)

        assertNotEquals(headerOf(a).salt.toList(), headerOf(b).salt.toList())
        assertNotEquals(headerOf(a).iv.toList(), headerOf(b).iv.toList())
    }

    // ---------------------------------------------------------------------
    // legacy format compat + upgrade
    // ---------------------------------------------------------------------

    @Test
    fun legacyBlob_decryptsAndYieldsUpgradedPbkdf2Blob() {
        val plaintext = privateKeyBytes()
        val legacy = buildLegacyBlob(material, plaintext)

        val result = newCipher().decrypt(legacy)
        assertArrayEquals(plaintext, result.plaintext)

        val upgraded = result.upgradedBlob
        assertNotNull("legacy blob must be re-encrypted", upgraded)
        assertEquals("SMSK", String(upgraded!!.copyOfRange(0, 4)))
        val header = headerOf(upgraded)
        assertEquals(StorageBlobCipher.KDF_ID_PBKDF2, header.kdfId)
        assertTrue(header.iterations >= 100_000)

        // upgraded blob is self-contained: decrypts to the same plaintext
        val redecrypt = newCipher().decrypt(upgraded)
        assertArrayEquals(plaintext, redecrypt.plaintext)
        assertNull(redecrypt.upgradedBlob)
    }

    @Test
    fun legacyBlob_wrongMaterial_failsDecryption() {
        val legacy = buildLegacyBlob("different-id:me.capcom.smsgateway", privateKeyBytes())

        assertThrows(GeneralSecurityException::class.java) { newCipher().decrypt(legacy) }
    }

    // ---------------------------------------------------------------------
    // tamper detection
    // ---------------------------------------------------------------------

    @Test
    fun tamperedSalt_failsDecryption() {
        val blob = newCipher().encrypt(privateKeyBytes())
        val tampered = blob.copyOf().also { it[10] = (it[10].toInt() xor 0x01).toByte() }

        assertThrows(GeneralSecurityException::class.java) { newCipher().decrypt(tampered) }
    }

    @Test
    fun tamperedIterations_failsDecryption() {
        val blob = newCipher().encrypt(privateKeyBytes())
        // flip the low byte: 100_000 -> 100_001 (stays within accepted bounds)
        val tampered = blob.copyOf().also { it[9] = (it[9].toInt() xor 0x01).toByte() }

        assertThrows(GeneralSecurityException::class.java) { newCipher().decrypt(tampered) }
    }

    @Test
    fun tamperedIv_failsDecryption() {
        val blob = newCipher().encrypt(privateKeyBytes())
        val tampered = blob.copyOf().also { it[30] = (it[30].toInt() xor 0x01).toByte() }

        assertThrows(GeneralSecurityException::class.java) { newCipher().decrypt(tampered) }
    }

    @Test
    fun tamperedCiphertext_failsDecryption() {
        val blob = newCipher().encrypt(privateKeyBytes())
        val tampered = blob.copyOf().also {
            it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte()
        }

        assertThrows(GeneralSecurityException::class.java) { newCipher().decrypt(tampered) }
    }

    // ---------------------------------------------------------------------
    // malformed / boundary inputs
    // ---------------------------------------------------------------------

    @Test
    fun emptyAndTruncatedBlobs_fail() {
        assertThrows(IllegalArgumentException::class.java) { newCipher().decrypt(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { newCipher().decrypt(ByteArray(5)) }
        // v1 header truncated (magic present but header incomplete)
        val truncated = ByteArray(6).also { byteArrayOf(0x53, 0x4D, 0x53, 0x4B).copyInto(it, 0) }
        assertThrows(IllegalArgumentException::class.java) { newCipher().decrypt(truncated) }
    }

    @Test
    fun unsupportedVersion_fails() {
        val blob = buildV1Blob(
            version = 2,
            kdfId = StorageBlobCipher.KDF_ID_PBKDF2,
            iterations = 100_000,
            salt = ByteArray(16),
            iv = ByteArray(12),
            ciphertext = ByteArray(16),
        )

        assertThrows(IllegalArgumentException::class.java) { newCipher().decrypt(blob) }
    }

    @Test
    fun unsupportedKdfId_fails() {
        val blob = buildV1Blob(
            version = 1,
            kdfId = 99,
            iterations = 100_000,
            salt = ByteArray(16),
            iv = ByteArray(12),
            ciphertext = ByteArray(16),
        )

        assertThrows(IllegalArgumentException::class.java) { newCipher().decrypt(blob) }
    }

    // ---------------------------------------------------------------------
    // input variation
    // ---------------------------------------------------------------------

    @Test
    fun roundTrip_varyingPlaintextSizes() {
        val sizes = intArrayOf(1, 2048, 5000)
        for (size in sizes) {
            val data = ByteArray(size).also { SecureRandom().nextBytes(it) }
            val blob = newCipher().encrypt(data)
            assertArrayEquals(data, newCipher().decrypt(blob).plaintext)
        }
    }
}
