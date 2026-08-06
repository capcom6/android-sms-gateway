package me.capcom.smsgateway.modules.receiver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

// The bounded read enforces MAX_INBOX_PART_BYTES BEFORE a provider part is
// materialized into heap: oversized streams abort with null instead of
// allocating the whole part.
internal class MmsContentReaderTest {

    @Test
    fun readBoundedReturnsAllBytesWhenBelowCap() {
        val data = ByteArray(100) { it.toByte() }

        val result = MmsContentReader.readBounded(ByteArrayInputStream(data), 1024)

        assertArrayEquals(data, result)
    }

    @Test
    fun readBoundedReturnsAllBytesAtExactCap() {
        val data = ByteArray(10) { it.toByte() }

        val result = MmsContentReader.readBounded(ByteArrayInputStream(data), 10)

        assertArrayEquals(data, result)
    }

    @Test
    fun readBoundedReturnsNullWhenStreamExceedsCap() {
        val data = ByteArray(11) { it.toByte() }

        assertNull(MmsContentReader.readBounded(ByteArrayInputStream(data), 10))
    }

    @Test
    fun readBoundedReturnsNullWithoutReadingWholeStream() {
        val data = ByteArray(1024 * 1024)

        assertNull(MmsContentReader.readBounded(ByteArrayInputStream(data), 16))
    }

    @Test
    fun readBoundedReturnsEmptyForEmptyStream() {
        assertArrayEquals(ByteArray(0), MmsContentReader.readBounded(ByteArrayInputStream(ByteArray(0)), 10))
    }
}
