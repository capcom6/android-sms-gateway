package me.capcom.smsgateway.modules.webhooks.workers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookWorkerPolicyTest {

    @Test
    fun runningProcessorIsKept() {
        assertFalse(shouldReplace(ProcessorActivity.Running))
    }

    @Test
    fun blockedProcessorIsKept() {
        assertFalse(shouldReplace(ProcessorActivity.Blocked))
    }

    @Test
    fun pendingImmediateWorkIsKept() {
        assertFalse(shouldReplace(ProcessorActivity.Pending))
    }

    @Test
    fun pendingDelayedWorkIsReplaced() {
        assertTrue(shouldReplace(ProcessorActivity.DelayedPending))
    }

    @Test
    fun idleProcessorIsReplaced() {
        assertTrue(shouldReplace(ProcessorActivity.Idle))
    }
}
