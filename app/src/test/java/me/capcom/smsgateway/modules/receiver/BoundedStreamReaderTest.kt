package me.capcom.smsgateway.modules.receiver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

internal class BoundedStreamReaderTest {
    @Test
    fun readsContentWithinTheLimit() {
        val content = "hello world".toByteArray()

        val result = BoundedStreamReader.read(content.inputStream(), 1024)

        assertFalse(result.truncated)
        assertArrayEquals(content, result.bytes)
    }

    @Test
    fun readsContentUpToTheLimit() {
        val content = ByteArray(64) { it.toByte() }

        val result = BoundedStreamReader.read(content.inputStream(), 64)

        assertFalse(result.truncated)
        assertArrayEquals(content, result.bytes)
    }

    @Test
    fun detectsContentBeyondTheLimit() {
        val content = ByteArray(65) { it.toByte() }

        val result = BoundedStreamReader.read(content.inputStream(), 64)

        assertTrue(result.truncated)
        assertNull(result.bytes)
    }

    @Test
    fun doesNotConsumeMoreThanTheLimitPlusOneByte() {
        val source = CountingInputStream(ByteArray(1024) { it.toByte() })

        val result = BoundedStreamReader.read(source, 64)

        assertTrue(result.truncated)
        assertEquals(65, source.consumed)
    }

    @Test
    fun readsEmptyStream() {
        val result = BoundedStreamReader.read(ByteArray(0).inputStream(), 64)

        assertFalse(result.truncated)
        assertEquals(0, result.bytes?.size)
    }

    @Test
    fun limitZeroTruncatesAnyContent() {
        val result = BoundedStreamReader.read(byteArrayOf(1).inputStream(), 0)

        assertTrue(result.truncated)
        assertNull(result.bytes)
    }

    private class CountingInputStream(private val source: ByteArray) : InputStream() {
        var consumed = 0
            private set

        override fun read(): Int {
            if (consumed >= source.size) return -1
            return source[consumed++].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (consumed >= source.size) return -1
            val count = minOf(length, source.size - consumed)
            System.arraycopy(source, consumed, buffer, offset, count)
            consumed += count
            return count
        }
    }
}
