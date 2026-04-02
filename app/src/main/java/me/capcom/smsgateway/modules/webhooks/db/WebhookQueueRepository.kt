package me.capcom.smsgateway.modules.webhooks.db

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.modules.webhooks.WebhookPayloadStorage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Repository for webhook queue operations.
 * Provides business logic and a clean API for the rest of the application.
 */
class WebhookQueueRepository(
    private val dao: WebhookQueueDao,
) : KoinComponent {
    private val payloadStorage: WebhookPayloadStorage by inject()

    /**
     * Enqueue a new webhook event for processing.
     */
    suspend fun enqueueWebhook(
        url: String,
        payload: String,
    ): String {
        val id = NanoIdUtils.randomNanoId()
        val payloadRef = payloadStorage.save(id, payload)

        try {
            dao.insertWebhook(
                WebhookQueueEntity(
                    id = id,
                    url = url,
                    payload = payloadRef,
                    status = WebhookStatus.PENDING,
                    createdAt = System.currentTimeMillis(),
                    nextAttempt = System.currentTimeMillis()
                )
             )
        } catch (e: Exception) {
            payloadStorage.delete(id)
            throw e
        }

        return id
    }

    /**
     * Check if there are any scheduled webhook events.
     */
    suspend fun hasScheduledWebhooks(): Boolean {
        return dao.scheduledWebhooksCount() > 0
    }

    /**
     * Get the next pending webhook events for processing.
     */
    suspend fun getPendingWebhooks(limit: Int = 10): List<WebhookQueueEntity> {
        return dao.getPendingWebhooks(limit = limit)
    }

    /**
     * Start processing a webhook by marking it as processing.
     */
    suspend fun startProcessing(webhookId: String) {
        dao.markAsProcessing(id = webhookId)
    }

    /**
     * Complete a webhook processing successfully.
     */
    suspend fun completeWebhook(webhookId: String) {
        payloadStorage.delete(webhookId)
        dao.markAsCompleted(id = webhookId)
    }

    /**
     * Mark webhook as failed and schedule retry.
     */
    suspend fun scheduleRetry(
        webhookId: String,
        error: String?,
        maxRetries: Int = 3,
        baseDelayMs: Long = 5000L
    ) {
        val webhook = dao.getById(webhookId)

        if (webhook.canRetry(maxRetries)) {
            val backoffDelay = calculateBackoffDelay(webhook.retryCount + 1, baseDelayMs)
            val nextAttempt = System.currentTimeMillis() + backoffDelay

            dao.markAsFailed(
                id = webhookId,
                nextAttempt = nextAttempt,
                error = error
            )
        } else {
            // Max retries exceeded, mark as permanently failed
            permanentlyFailWebhook(
                webhookId = webhookId,
                error = error ?: "Max retries exceeded",
            )
        }
    }

    /**
     * Permanently fail a webhook.
     */
    suspend fun permanentlyFailWebhook(webhookId: String, error: String) {
        payloadStorage.delete(webhookId.toString())
        dao.markAsPermanentlyFailed(
            id = webhookId,
            error = error
        )
    }

    /**
     * Clean up old webhook entries to prevent database bloat.
     */
    suspend fun cleanupOldEntries(retentionDays: Int = 7) {
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val oldEntryIds = dao.getOldEntryIds(cutoffTime)
        oldEntryIds.forEach { payloadStorage.delete(it) }
        dao.cleanupOldEntries(oldEntryIds)
    }

    /**
     * Recover stuck processing webhooks (timed out workers).
     */
    suspend fun recoverStuckWebhooks(timeoutMinutes: Long = 5) {
        val timeoutThreshold = System.currentTimeMillis() - (timeoutMinutes * 60 * 1000L)
        dao.recoverStuckProcessingWebhooks(timeoutThreshold)
    }

    /**
     * Calculate backoff delay for retries using exponential backoff.
     */
    private fun calculateBackoffDelay(retryCount: Int, baseDelayMs: Long): Long {
        // Exponential backoff: baseDelay * 2^(retryCount - 1)
        val multiplier = (1L shl (retryCount - 1))
        return baseDelayMs * multiplier
    }
}

/**
 * Extension function to check if webhook can be retried.
 */
fun WebhookQueueEntity.canRetry(maxRetries: Int = 3): Boolean {
    return retryCount < maxRetries && status != WebhookStatus.PERMANENTLY_FAILED
}
