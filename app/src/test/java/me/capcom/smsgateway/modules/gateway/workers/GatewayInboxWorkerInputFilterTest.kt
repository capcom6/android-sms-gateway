package me.capcom.smsgateway.modules.gateway.workers

import androidx.work.Data
import me.capcom.smsgateway.modules.gateway.InboxUploadFilter
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Worker input-data (de)serialization for the optional since/until/types filter.
internal class GatewayInboxWorkerInputFilterTest {

    @Test
    fun emptyDataYieldsNoFilter() {
        val filter = uploadFilterFromData(Data.EMPTY)

        assertNull(filter.since)
        assertNull(filter.until)
        assertNull(filter.types)
    }

    @Test
    fun fullFilterRoundTrips() {
        val data = Data.Builder()
            .put(KEY_SINCE, 1000L)
            .put(KEY_UNTIL, 999000L)
            .put(KEY_TYPES, "SMS,MMS,MMS_DOWNLOADED")
            .build()

        val filter = uploadFilterFromData(data)

        assertEquals(1000L, filter.since)
        assertEquals(999000L, filter.until)
        assertEquals(
            setOf(IncomingMessageType.SMS, IncomingMessageType.MMS, IncomingMessageType.MMS_DOWNLOADED),
            filter.types,
        )
    }

    @Test
    fun singleTypeParses() {
        val data = Data.Builder().put(KEY_TYPES, "DATA_SMS").build()

        assertEquals(setOf(IncomingMessageType.DATA_SMS), uploadFilterFromData(data).types)
    }

    @Test
    fun partialFilterKeepsNulls() {
        val data = Data.Builder().put(KEY_UNTIL, 42L).build()

        val filter = uploadFilterFromData(data)
        assertNull(filter.since)
        assertEquals(42L, filter.until)
        assertNull(filter.types)
    }
}