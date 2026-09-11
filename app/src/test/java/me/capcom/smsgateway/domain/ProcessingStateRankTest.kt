package me.capcom.smsgateway.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rank ordering is what stops a late platform broadcast from moving a
 * recipient backwards. Pure Kotlin, no Android dependencies, so it runs in
 * the existing `./gradlew test` step.
 */
class ProcessingStateRankTest {

    private fun allowed(from: ProcessingState, to: ProcessingState) = from.rank <= to.rank

    @Test
    fun `normal progression is allowed`() {
        assertTrue(allowed(ProcessingState.Pending, ProcessingState.Processed))
        assertTrue(allowed(ProcessingState.Processed, ProcessingState.Sent))
        assertTrue(allowed(ProcessingState.Sent, ProcessingState.Delivered))
    }

    @Test
    fun `the reported race cannot regress a recipient`() {
        // The send loop writes Processed after dispatching; the platform's
        // ACTION_SENT arrives on another coroutine. Whichever landed last
        // used to win, and a recipient clobbered back to Processed never
        // left it when delivery reports were off.
        assertTrue("Sent must not be overwritten by Processed",
            !allowed(ProcessingState.Sent, ProcessingState.Processed))
        assertTrue("Delivered must not be regressed to Sent",
            !allowed(ProcessingState.Delivered, ProcessingState.Sent))
        assertTrue("Delivered must not be regressed to Processed",
            !allowed(ProcessingState.Delivered, ProcessingState.Processed))
    }

    @Test
    fun `a failure stays authoritative whenever it arrives`() {
        // This preserves the behaviour of the previous guard, which was
        // `state <> 'Failed'` — anything could become Failed.
        for (from in ProcessingState.values()) {
            assertTrue(
                "$from should still be able to become Failed",
                allowed(from, ProcessingState.Failed)
            )
        }
    }

    @Test
    fun `cancellation is reachable from the states that precede it`() {
        assertTrue(allowed(ProcessingState.Pending, ProcessingState.Cancelling))
        assertTrue(allowed(ProcessingState.Cancelling, ProcessingState.Cancelled))
        assertTrue(allowed(ProcessingState.Pending, ProcessingState.Cancelled))
    }

    @Test
    fun `re-writing the same state is allowed so error text can be updated`() {
        for (state in ProcessingState.values()) {
            assertTrue("$state should be re-writable", allowed(state, state))
        }
    }

    @Test
    fun `pending is the lowest rank so reconciliation writes are never blocked`() {
        // MessagesRepository.getPending() selects WHERE state = 'Pending' and
        // then syncs the denormalised message column in a `while (true)`
        // loop. If that update could be blocked, the loop would spin forever
        // and wedge the send worker.
        val lowest = ProcessingState.values().minOf { it.rank }
        assertEquals(lowest, ProcessingState.Pending.rank)
    }

    @Test
    fun `terminal states rank highest`() {
        val maxRank = ProcessingState.values().maxOf { it.rank }
        assertEquals(maxRank, ProcessingState.Failed.rank)
        assertEquals(maxRank, ProcessingState.Cancelled.rank)
    }
}
