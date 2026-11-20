package me.capcom.smsgateway.modules.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class RecentSmsTest {
    private var now = 1_000_000L
    private fun registry(max: Int = 64) =
        RecentSms(ttlMs = 10_000, maxEntries = max, clock = { now })

    private fun RecentSms.subs(address: String, text: String, date: Long) =
        candidates(address, text, date).map { it.subscriptionId }

    @Test
    fun findsTheTwinOfAnObserverRowOnEitherSlot() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 1)
        r.record("SENDER", "hello two", 6_000, 3)

        assertEquals(listOf<Int?>(1), r.subs("SENDER", "hello", 5_000))
        assertEquals(listOf<Int?>(3), r.subs("SENDER", "hello two", 6_000))
    }

    @Test
    fun sameTextOnTwoSimsGivesTwoCandidates() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 1)
        r.record("SENDER", "hello", 5_500, 3)

        assertEquals(listOf<Int?>(1, 3), r.subs("SENDER", "hello", 5_200))
    }

    @Test
    fun matchesWithinDateToleranceOnly() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 3)

        assertEquals(1, r.candidates("SENDER", "hello", 5_000 + RecentSms.DATE_TOLERANCE_MS).size)
        assertEquals(1, r.candidates("SENDER", "hello", 5_000 - RecentSms.DATE_TOLERANCE_MS).size)
        assertTrue(r.candidates("SENDER", "hello", 5_000 + RecentSms.DATE_TOLERANCE_MS + 1).isEmpty())
    }

    @Test
    fun differentTextOrSenderIsNotATwin() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 3)

        assertTrue(r.candidates("SENDER", "hello!", 5_000).isEmpty())
        assertTrue(r.candidates("OTHER", "hello", 5_000).isEmpty())
    }

    @Test
    fun senderCompareIgnoresCaseAndSurroundingSpace() {
        val r = registry()
        r.record("Sender ", "hello", 5_000, 1)

        assertEquals(1, r.candidates("sender", "hello", 5_000).size)
    }

    @Test
    fun entryWithUnknownSubscriptionIsStillACandidate() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, null)

        assertEquals(listOf<Int?>(null), r.subs("SENDER", "hello", 5_000))
    }

    @Test
    fun consumedEntryIsMatchedOnlyOnce() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 1)
        r.record("SENDER", "hello", 6_000, 1)

        val first = r.candidates("SENDER", "hello", 5_500)
        assertEquals(2, first.size)
        assertTrue(r.consume(first[0]))
        assertFalse(r.consume(first[0]))
        assertEquals(1, r.candidates("SENDER", "hello", 5_500).size)
    }

    @Test
    fun anIdenticalCopyIsNotRecordedTwice() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 1)
        r.record("SENDER", "hello", 5_000, 1)

        assertEquals(1, r.candidates("SENDER", "hello", 5_000).size)
    }

    @Test
    fun entriesExpire() {
        val r = registry()
        r.record("SENDER", "hello", 5_000, 1)

        now += 10_001
        assertTrue(r.candidates("SENDER", "hello", 5_000).isEmpty())
    }

    @Test
    fun oldestEntriesAreDroppedBeyondTheCap() {
        val r = registry(max = 2)
        r.record("A", "1", 1_000, 1)
        r.record("A", "2", 2_000_000, 1)
        r.record("A", "3", 3_000_000, 3)

        assertTrue(r.candidates("A", "1", 1_000).isEmpty())
        assertEquals(1, r.candidates("A", "2", 2_000_000).size)
        assertEquals(1, r.candidates("A", "3", 3_000_000).size)
    }

    @Test
    fun sameMessageIsSymmetric() {
        assertTrue(RecentSms.sameMessage("a", "t", 10, "A", "t", 12))
        assertFalse(RecentSms.sameMessage("a", "t", 10, "A", "u", 12))
    }
}
