package me.capcom.smsgateway.modules.gateway.workers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T7 pull-trigger coalescing.
 *
 * The storm mechanism (T1-verified): every MessageEnqueued/Ping event and
 * every SSE onConnected called PullMessagesWorker.start(), whose
 * ExistingPeriodicWorkPolicy.REPLACE on a 15-min periodic work triggers an
 * immediate run - one full batch GET per enqueue event (~1.35/s).
 *
 * These tests prove the AC "burst: N triggers coalesce to minimal pulls",
 * that a single (first) trigger still pulls promptly, and that a trigger
 * inside the window asks for a trailing pull (returned remaining millis)
 * instead of being dropped until the 15-min periodic worker:
 * - first trigger acquires at once -> first-pull latency ~0 (leading edge);
 * - N triggers inside the cooldown produce exactly 1 immediate pull and a
 *   remaining delay that maps to the end of the window;
 * - a trigger at/after the cooldown boundary acquires again (bounded
 *   staleness).
 */
class PullTriggerGateTest {

    private var now = 0L
    private val gate = PullTriggerGate(cooldownMillis = 5_000L) { now }

    @Test
    fun singleTriggerDispatchesPromptlyWithoutWaiting() {
        // leading edge: the very first event trigger must not be delayed
        assertEquals("first trigger must acquire immediately", 0L, gate.tryAcquire())
    }

    @Test
    fun burstOfTriggersCoalescesToOnePull() {
        var pulls = 0
        var trailingDelay = 0L
        repeat(100) {
            val remaining = gate.tryAcquire()
            if (remaining == 0L) pulls++ else trailingDelay = remaining
        }

        assertEquals("100 triggers inside one window must dispatch 1 pull", 1, pulls)
        assertEquals("suppressed triggers must defer to the window end", 5_000L, trailingDelay)
    }

    @Test
    fun triggerJustBeforeCooldownBoundaryIsSuppressed() {
        assertEquals(0L, gate.tryAcquire())
        now = 4_999L

        assertEquals(
            "trigger inside the cooldown must defer to the window end",
            1L,
            gate.tryAcquire()
        )
    }

    @Test
    fun triggerAtCooldownBoundaryAcquiresAgain() {
        assertEquals(0L, gate.tryAcquire())
        now = 5_000L

        assertEquals("trigger at the boundary must dispatch the next pull", 0L, gate.tryAcquire())
    }

    @Test
    fun cooldownRestartsFromEachAcquire() {
        assertEquals(0L, gate.tryAcquire())      // t=0
        now = 5_000L
        assertEquals(0L, gate.tryAcquire())      // t=5000 - new window starts here
        now = 5_001L
        assertEquals(4_999L, gate.tryAcquire())  // deferred to t=10_000
        now = 9_999L
        assertEquals(1L, gate.tryAcquire())      // still inside second window
        now = 10_000L
        assertEquals(0L, gate.tryAcquire())      // second window elapsed
    }

    @Test
    fun cooldownLengthIsRespectedAcrossVariants() {
        var now1k = 0L
        val gate1s = PullTriggerGate(cooldownMillis = 1_000L) { now1k }

        assertEquals(0L, gate1s.tryAcquire())
        now1k = 999L
        assertEquals(1L, gate1s.tryAcquire())
        now1k = 1_000L
        assertEquals(0L, gate1s.tryAcquire())
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
            if (gate.tryAcquire() == 0L) pulls++
        }

        assertEquals("61 triggers across 1 window + 1 late must dispatch 2 pulls", 2, pulls)
    }

    /**
     * Regression for the Greptile P1: a message event that arrives after the
     * leading pull must not be discarded. The gate returns the remaining
     * cooldown, the worker schedules a trailing pull for that delay, and the
     * trailing pull dispatches when the window elapses - all within one
     * cooldown instead of waiting for the 15-min periodic worker.
     */
    @Test
    fun messageArrivingAfterFirstPullSchedulesTrailingPullWithinOneWindow() {
        // leading event at t=0 dispatches the first pull
        assertEquals(0L, gate.tryAcquire())

        // a message is enqueued at t=1s, after the first pull finished
        now = 1_000L
        val trailingDelay = gate.tryAcquire()

        assertEquals("follow-up must fire at the end of the window", 4_000L, trailingDelay)

        // the scheduled follow-up runs at t=5s and dispatches the pull
        now += trailingDelay
        assertEquals(0L, gate.tryAcquire())
    }
}
