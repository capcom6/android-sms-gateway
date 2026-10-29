package me.capcom.smsgateway.modules.gateway.workers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T7 pull-trigger coalescing.
 *
 * The storm mechanism (T1-verified): every MessageEnqueued/Ping event and
 * every SSE onConnected called PullMessagesWorker.start(), whose
 * ExistingPeriodicWorkPolicy.REPLACE on a 15-min periodic work triggers an
 * immediate run - one full batch GET per enqueue event (~1.35/s).
 *
 * These tests prove the AC "burst: N triggers coalesce to minimal pulls"
 * and that a single (first) trigger still pulls promptly:
 * - first trigger acquires at once -> first-pull latency ~0 (leading edge);
 * - N triggers inside the cooldown produce exactly 1 pull dispatch;
 * - a trigger at/after the cooldown boundary acquires again (bounded
 *   staleness); the un-coalesced worst case is the 15-min periodic worker
 *   if the trigger gate state is lost (process death / cancelled work).
 */
class PullTriggerGateTest {

    private var now = 0L
    private val gate = PullTriggerGate(cooldownMillis = 5_000L) { now }

    @Test
    fun singleTriggerDispatchesPromptlyWithoutWaiting() {
        // leading edge: the very first event trigger must not be delayed
        assertTrue("first trigger must acquire immediately", gate.tryAcquire())
    }

    @Test
    fun burstOfTriggersCoalescesToOnePull() {
        var pulls = 0
        repeat(100) {
            if (gate.tryAcquire()) pulls++
        }

        assertEquals("100 triggers inside one window must dispatch 1 pull", 1, pulls)
    }

    @Test
    fun triggerJustBeforeCooldownBoundaryIsSuppressed() {
        assertTrue(gate.tryAcquire())
        now = 4_999L

        assertFalse("trigger inside the cooldown must be coalesced away", gate.tryAcquire())
    }

    @Test
    fun triggerAtCooldownBoundaryAcquiresAgain() {
        assertTrue(gate.tryAcquire())
        now = 5_000L

        assertTrue("trigger at the boundary must dispatch the next pull", gate.tryAcquire())
    }

    @Test
    fun cooldownRestartsFromEachAcquire() {
        assertTrue(gate.tryAcquire())      // t=0
        now = 5_000L
        assertTrue(gate.tryAcquire())      // t=5000 - new window starts here
        now = 9_999L
        assertFalse(gate.tryAcquire())     // inside second window
        now = 10_000L
        assertTrue(gate.tryAcquire())      // second window elapsed
    }

    @Test
    fun cooldownLengthIsRespectedAcrossVariants() {
        var now1k = 0L
        val gate1s = PullTriggerGate(cooldownMillis = 1_000L) { now1k }

        assertTrue(gate1s.tryAcquire())
        now1k = 999L
        assertFalse(gate1s.tryAcquire())
        now1k = 1_000L
        assertTrue(gate1s.tryAcquire())
    }

    @Test
    fun simulatedEventStormDispatchesOncePerWindowNotOncePerTrigger() {
        // End-to-end shape of the AC: a 60-event storm spread over 4 seconds
        // (well inside one 5s window) plus one late event after the window.
        var pulls = 0
        val eventTimes = (0 until 60).map { it * 66L }   // 0..3894 ms
        val allTimes = eventTimes + 6_500L               // late event after window

        allTimes.forEach { t ->
            now = t
            if (gate.tryAcquire()) pulls++
        }

        assertEquals("61 triggers across 1 window + 1 late must dispatch 2 pulls", 2, pulls)
    }
}
