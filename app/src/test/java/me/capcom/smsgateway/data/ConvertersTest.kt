package me.capcom.smsgateway.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun dateFromString_canonical_roundTrips() {
        val str = "2026-08-28T12:00:00.000Z"
        val parsed = converters.dateFromString(str)
        assertEquals(str, converters.dateToString(parsed))
    }

    @Test
    fun dateToString_thenFromString_preservesTime() {
        val original = Date(1_768_000_000_000L)
        val str = converters.dateToString(original)
        assertEquals(original.time, converters.dateFromString(str)?.time)
    }

    @Test
    fun dateFromString_noMillis_doesNotThrow() {
        // Legacy value produced by MIGRATION_7_8 via strftime('%FT%TZ', ...) (no .SSS).
        val value = "2026-08-28T12:00:00"
        val parsed = converters.dateFromString(value)
        // Parsed as GMT; 12:00:00 UTC of that date.
        val expected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.parse(value)
        assertEquals(expected?.time, parsed?.time)
    }

    @Test
    fun dateFromString_noMillisWithZ_doesNotThrow() {
        val value = "2026-08-28T12:00:00Z"
        val parsed = converters.dateFromString(value)
        val expected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.parse("2026-08-28T12:00:00")
        assertEquals(expected?.time, parsed?.time)
    }

    @Test
    fun dateFromString_spaceSeparator_doesNotThrow() {
        val value = "2026-08-28 12:00:00"
        val parsed = converters.dateFromString(value)
        val expected = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.parse(value)
        assertEquals(expected?.time, parsed?.time)
    }

    @Test
    fun dateFromString_null_returnsNull() {
        assertNull(converters.dateFromString(null))
    }

    @Test
    fun dateFromString_unparseable_returnsNull() {
        assertNull(converters.dateFromString("not-a-date"))
    }
}
