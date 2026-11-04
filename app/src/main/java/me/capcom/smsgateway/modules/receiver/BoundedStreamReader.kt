package me.capcom.smsgateway.modules.receiver

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads a stream without buffering more than the requested amount of data.
 *
 * At most `limit + 1` bytes are read from the source, which is enough to
 * detect that the source holds more data than allowed while keeping the
 * memory footprint bounded.
 */
internal object BoundedStreamReader {
    private const val BUFFER_SIZE = 8 * 1024

    data class Result(
        val bytes: ByteArray?,
        val truncated: Boolean,
    )

    /**
     * @param limit maximum number of bytes to read, `0` reads nothing
     * @return the read bytes, or `null` when the source was truncated
     */
    fun read(source: InputStream, limit: Int): Result {
        require(limit >= 0) { "limit must not be negative" }

        val buffer = ByteArray(BUFFER_SIZE)
        val output = ByteArrayOutputStream(minOf(limit, BUFFER_SIZE))
        var total = 0

        while (total <= limit) {
            val read = source.read(buffer, 0, minOf(buffer.size, limit + 1 - total))
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
        }

        return if (total > limit) {
            Result(bytes = null, truncated = true)
        } else {
            Result(bytes = output.toByteArray(), truncated = false)
        }
    }
}
