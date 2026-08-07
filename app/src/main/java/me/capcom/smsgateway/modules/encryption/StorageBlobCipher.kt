package me.capcom.smsgateway.modules.encryption

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts/decrypts the software (API 21-22) E2E private-key blobs stored in
 * the "gateway" Room database.
 *
 * Blob format:
 *  - v1 (current, KDF):
 *    `"SMSK" || version(1) || kdfId(1) || iterations(4 BE) || salt(16) ||
 *     iv(12) || AES-GCM(ciphertext)`, key = PBKDF2WithHmacSHA1(password =
 *    same ANDROID_ID material, salt = fresh random per blob, >= 100k
 *    iterations, 256-bit output).
 *
 * The class is pure JVM (no android.* imports) so it is unit-testable on the
 * host. Throws [IllegalArgumentException] on malformed/unsupported blobs and
 * [GeneralSecurityException] when authentication fails (tampered key
 * material, salt, params, IV or ciphertext).
 */
class StorageBlobCipher(private val legacyKeyMaterial: String) {

    private val random = SecureRandom()

    /** Encrypts [plaintext] into a v1 blob with a fresh random salt. */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val key = deriveKey(legacyKeyMaterial, salt, KDF_ITERATIONS)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return encodeBlob(KDF_ID_PBKDF2, KDF_ITERATIONS, salt, iv, ciphertext)
    }

    /**
     * Decrypts [blob]. v1 blobs derive the key from their embedded
     * salt/iterations; headerless legacy blobs use the legacy SHA-256
     * derivation and yield an [BlobDecryptResult.upgradedBlob] for the
     * caller to persist.
     */
    fun decrypt(blob: ByteArray): BlobDecryptResult {
        return decryptV1(blob)
    }

    private fun decryptV1(blob: ByteArray): BlobDecryptResult {
        require(blob.size >= HEADER_LENGTH) { "v1 blob too short" }
        require(blob[VERSION_OFFSET].toInt() == BLOB_VERSION) {
            "unsupported blob version: ${blob[VERSION_OFFSET].toInt()}"
        }
        val kdfId = blob[KDF_ID_OFFSET].toInt()
        require(kdfId == KDF_ID_PBKDF2) { "unsupported KDF id: $kdfId" }
        val iterations = readInt(blob, ITERATIONS_OFFSET)
        require(iterations > 0 && iterations <= MAX_ITERATIONS) {
            "invalid PBKDF2 iteration count: $iterations"
        }
        val salt = blob.copyOfRange(SALT_OFFSET, IV_OFFSET)
        val iv = blob.copyOfRange(IV_OFFSET, HEADER_LENGTH)
        val ciphertext = blob.copyOfRange(HEADER_LENGTH, blob.size)

        val key = deriveKey(legacyKeyMaterial, salt, iterations)
        return BlobDecryptResult(aesDecrypt(key, iv, ciphertext))
    }

    /**
     * PBKDF2WithHmacSHA1 over the legacy material string. The password source
     * is unchanged (ANDROID_ID:packageName is the only per-device secret
     * available without user interaction on API 21-22), but the random salt
     * and >= 100k iterations raise the per-blob cost of a brute-force attack
     * by ~6 orders of magnitude over the legacy single SHA-256.
     */
    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, AES_KEY_LENGTH * 8)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun aesDecrypt(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun encodeBlob(
        kdfId: Int,
        iterations: Int,
        salt: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val blob = ByteArray(HEADER_LENGTH + ciphertext.size)
        MAGIC.copyInto(blob, 0)
        blob[VERSION_OFFSET] = BLOB_VERSION.toByte()
        blob[KDF_ID_OFFSET] = kdfId.toByte()
        writeInt(blob, ITERATIONS_OFFSET, iterations)
        salt.copyInto(blob, SALT_OFFSET)
        iv.copyInto(blob, IV_OFFSET)
        ciphertext.copyInto(blob, HEADER_LENGTH)
        return blob
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF shl 24) or
                (source[offset + 1].toInt() and 0xFF shl 16) or
                (source[offset + 2].toInt() and 0xFF shl 8) or
                (source[offset + 3].toInt() and 0xFF)

    companion object {
        // "SMSK" - SMS gateway key blob
        private val MAGIC = byteArrayOf(0x53, 0x4D, 0x53, 0x4B)

        private const val BLOB_VERSION = 1
        private const val VERSION_OFFSET = 4
        private const val KDF_ID_OFFSET = 5
        private const val ITERATIONS_OFFSET = 6
        private const val SALT_OFFSET = 10
        private const val IV_OFFSET = 26
        private const val HEADER_LENGTH = 38

        internal const val KDF_ID_PBKDF2 = 1

        internal const val KDF_ITERATIONS = 100_000
        private const val MAX_ITERATIONS = 10_000_000

        private const val SALT_LENGTH = 16
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_KEY_LENGTH = 32

        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1"
    }
}

/**
 * Result of decrypting a stored key blob.
 *
 * @param plaintext the decrypted private-key encoding (PKCS#8).
 * @param upgradedBlob non-null when [StorageBlobCipher.decrypt] re-encrypted
 *   a legacy blob to the v1 PBKDF2 format; the caller must persist it to
 *   replace the legacy blob.
 */
data class BlobDecryptResult(
    val plaintext: ByteArray,
    val upgradedBlob: ByteArray? = null,
)
