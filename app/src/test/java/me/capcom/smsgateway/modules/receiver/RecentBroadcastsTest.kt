package me.capcom.smsgateway.modules.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class RecentBroadcastsTest {
    private var now = 1_000_000L
    private fun registry(max: Int = 64) =
        RecentBroadcasts(ttlMs = 10_000, maxEntries = max, clock = { now })

    @Test
    fun findsTheBroadcastTwinOfAnObserverRowOnEitherSlot() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 1)
        r.record("SENDER", "hello two", 6_000, 3)

        assertEquals(1, r.find("SENDER", "hello", 5_000)?.subscriptionId)
        assertEquals(3, r.find("SENDER", "hello two", 6_000)?.subscriptionId)
    }

    @Test
    fun matchesWithinDateToleranceOnly() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 3)

        assertNotNull(r.find("SENDER", "hello", 5_000 + RecentBroadcasts.DATE_TOLERANCE_MS))
        assertNotNull(r.find("SENDER", "hello", 5_000 - RecentBroadcasts.DATE_TOLERANCE_MS))
        assertNull(r.find("SENDER", "hello", 5_000 + RecentBroadcasts.DATE_TOLERANCE_MS + 1))
    }

    @Test
    fun differentTextOrSenderIsNotATwin() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 3)

        assertNull(r.find("SENDER", "hello!", 5_000))
        assertNull(r.find("OTHER", "hello", 5_000))
    }

    @Test
    fun senderCompareIgnoresCaseAndSurroundingSpace() {
        val r = registry()
        r.record("Sender ", "hello", 5_000, 1)

        assertNotNull(r.find("sender", "hello", 5_000))
    }

    @Test
    fun entryWithUnknownSubscriptionIsStillATwin() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, null)

        val entry = r.find("SENDER", "hello", 5_000)
        assertNotNull(entry)
        assertNull(entry!!.subscriptionId)
    }

    @Test
    fun entriesExpire() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 1)

        now += 10_001
        assertNull(r.find("SENDER", "hello", 5_000))
    }

    @Test
    fun oldestEntriesAreDroppedBeyondTheCap() {
        val r = registry(max = 2)
        r.record("A", "1", 1_000, 1)
        r.record("A", "2", 2_000_000, 1)
        r.record("A", "3", 3_000_000, 3)

        assertNull(r.find("A", "1", 1_000))
        assertNotNull(r.find("A", "2", 2_000_000))
        assertNotNull(r.find("A", "3", 3_000_000))
    }

    @Test
    fun sameMessageIsSymmetric() {
        assertTrue(RecentBroadcasts.sameMessage("a", "t", 10, "A", "t", 12))
        assertFalse(RecentBroadcasts.sameMessage("a", "t", 10, "A", "u", 12))
    }
}
