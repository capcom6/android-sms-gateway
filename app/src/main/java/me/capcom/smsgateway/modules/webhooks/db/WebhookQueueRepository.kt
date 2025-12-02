package me.capcom.smsgateway.modules.webhooks.db

/**
 * Repository for webhook queue operations.
 * Provides business logic and a clean API for the rest of the application.
 */
class WebhookQueueRepository(
    private val dao: WebhookQueueDao,
) {
    /**
     * Enqueue a new webhook event for processing.
     */
    suspend fun enqueueWebhook(
        url: String,
        payload: String,
    ): Long {
        return dao.insertWebhook(
            WebhookQueueEntity(
                url = url,
                payload = payload,
                status = WebhookStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                nextAttempt = System.currentTimeMillis()
            )
        )
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
    suspend fun startProcessing(webhookId: Long) {
        dao.markAsProcessing(id = webhookId)
    }

    /**
     * Complete a webhook processing successfully.
     */
    suspend fun completeWebhook(webhookId: Long) {
        dao.markAsCompleted(id = webhookId)
    }

    /**
     * Mark webhook as failed and schedule retry.
     */
    suspend fun scheduleRetry(
        webhookId: Long,
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
            dao.markAsPermanentlyFailed(
                id = webhookId,
                error = error ?: "Max retries exceeded",
            )
        }
    }

    /**
     * Permanently fail a webhook.
     */
    suspend fun permanentlyFailWebhook(webhookId: Long, error: String) {
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
        dao.cleanupOldEntries(cutoffTime)
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

/**
 * Extension function to check if webhook is in processing state.
 */
fun WebhookQueueEntity.isProcessing(): Boolean {
    return WebhookStatus.isProcessing(status)
}