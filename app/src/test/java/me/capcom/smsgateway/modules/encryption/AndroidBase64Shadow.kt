// Test-only shadow of android.util.Base64 for JVM unit tests.
// The AGP mockable android.jar stubs every android.util.Base64 method with
// RuntimeException("not mocked"), so local unit tests cannot exercise the
// real class. This shadow provides a faithful implementation on top of
// java.util.Base64 (Java 8+), placed in the same package so it is resolved
// instead of the stub at unit-test runtime. @JvmStatic is REQUIRED: main
// sources compile against the real android.jar (static methods), and the
// unit-test runtime loads this class, so it must expose matching statics.
// It is never compiled into the app.
package android.util

object Base64 {
    const val DEFAULT: Int = 0

    const val NO_WRAP: Int = 2

    const val CRLF: Int = 4

    const val URL_SAFE: Int = 8

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String =
        encoderFor(flags).encodeToString(input)

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray =
        encoderFor(flags).encode(input)

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray =
        decoderFor(flags).decode(str)

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray =
        decoderFor(flags).decode(input)

    private fun encoderFor(flags: Int): java.util.Base64.Encoder =
        if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()

    private fun decoderFor(flags: Int): java.util.Base64.Decoder =
        if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder() else java.util.Base64.getDecoder()
}