package android.util

import java.util.Base64

/**
 * Test-only shadow of android.util.Base64. AGP's mockable android.jar throws
 * "not mocked" for this class in local JVM unit tests, but production code
 * (PassphraseDecryptor) must keep android.util.Base64 for minSdk 21. This
 * shadow mirrors the Android contract for the flags used by production
 * (DEFAULT, NO_WRAP, NO_PADDING, CRLF, URL_SAFE) with a faithful JVM
 * implementation; the unit-test output directory precedes android.jar on the
 * test runtime classpath.
 */
object Base64 {

    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        val decoder = if (flags and URL_SAFE != 0) {
            Base64.getUrlDecoder()
        } else {
            Base64.getDecoder()
        }
        return decoder.decode(str.filterNot { it.isWhitespace() })
    }

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        val encoder = if (flags and URL_SAFE != 0) {
            Base64.getUrlEncoder()
        } else {
            Base64.getEncoder()
        }
        return if (flags and NO_PADDING != 0) {
            encoder.withoutPadding().encodeToString(input)
        } else {
            encoder.encodeToString(input)
        }
    }
}
