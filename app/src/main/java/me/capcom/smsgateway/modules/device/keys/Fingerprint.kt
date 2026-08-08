package me.capcom.smsgateway.modules.device.keys

import java.security.MessageDigest

/**
 * Computes the out-of-band verification fingerprint of a device public key.
 *
 * The fingerprint is the SHA-256 digest of the X.509 SPKI DER bytes,
 * displayed as uppercase hex in 16 groups of 4 characters separated by
 * colons, e.g. `A1B2:C3D4:E5F6:7890:ABCD:EF12:3456:7890`.
 */
object Fingerprint {

    /**
     * Formats the fingerprint of the given X.509 SPKI DER key bytes, or
     * null when [derBytes] is null.
     */
    fun format(derBytes: ByteArray?): String? {
        if (derBytes == null) return null

        val digest = MessageDigest.getInstance("SHA-256").digest(derBytes)
        val hex = digest.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        return hex.chunked(4).joinToString(":")
    }
}