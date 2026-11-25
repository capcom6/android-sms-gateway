package me.capcom.smsgateway.modules.webhooks

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookQueueCoordinatorTest {

    @Test
    fun finalizeFromCurrentWorkerMarksFinalized() = runBlocking {
        val token = WebhookQueueCoordinator.begin()

        WebhookQueueCoordinator.finalize(token)

        assertTrue(WebhookQueueCoordinator.isFinalized())
    }

    @Test
    fun finalizeFromStaleWorkerIsIgnored() = runBlocking {
        val staleToken = WebhookQueueCoordinator.begin()
        WebhookQueueCoordinator.begin()

        WebhookQueueCoordinator.finalize(staleToken)

        assertFalse(WebhookQueueCoordinator.isFinalized())
    }

    @Test
    fun newWorkerStartResetsFinalized() = runBlocking {
        val token = WebhookQueueCoordinator.begin()
        WebhookQueueCoordinator.finalize(token)

        WebhookQueueCoordinator.begin()

        assertFalse(WebhookQueueCoordinator.isFinalized())
    }
}
