package me.stappmus.messagegateway.modules.webhooks.db

import androidx.room.*

/**
 * Data Access Object for webhook queue operations.
 */
@Dao
interface WebhookQueueDao {

    /**
     * Insert a new webhook event into the queue.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWebhook(webhook: WebhookQueueEntity): Long

    /**
     * Get webhook by id.
     */
    @Query("SELECT * FROM webhook_queue WHERE id = :id")
    suspend fun getById(id: Long): WebhookQueueEntity

    /**
     * Check if there are any scheduled webhook events.
     */
    @Query("SELECT COUNT(*) FROM webhook_queue WHERE status IN ('pending', 'failed')")
    suspend fun scheduledWebhooksCount(): Long

    /**
     * Get all pending webhook events ordered by next attempt time.
     */
    @Query(
        """
        SELECT * FROM webhook_queue 
        WHERE status IN ("pending", "failed") AND next_attempt <= :currentTime 
        ORDER BY next_attempt ASC 
        LIMIT :limit
    """
    )
    suspend fun getPendingWebhooks(
        currentTime: Long = System.currentTimeMillis(),
        limit: Int = 10
    ): List<WebhookQueueEntity>

    /**
     * Mark webhook as processing.
     */
    @Query(
        """
        UPDATE webhook_queue 
        SET status = "processing" 
        WHERE id = :id
    """
    )
    suspend fun markAsProcessing(
        id: Long,
    )

    /**
     * Update retry information and set next attempt time.
     */
    @Query(
        """
        UPDATE webhook_queue 
        SET status = "failed", 
            retry_count = retry_count + 1, 
            next_attempt = :nextAttempt,
            last_error = :error
        WHERE id = :id
    """
    )
    suspend fun markAsFailed(
        id: Long,
        nextAttempt: Long,
        error: String?,
    )

    /**
     * Mark webhook as completed.
     */
    @Query(
        """
        UPDATE webhook_queue 
        SET status = "completed" 
        WHERE id = :id
    """
    )
    suspend fun markAsCompleted(
        id: Long,
    )

    /**
     * Mark webhook as permanently failed.
     */
    @Query(
        """
        UPDATE webhook_queue 
        SET status = "permanently_failed", last_error = :error
        WHERE id = :id
    """
    )
    suspend fun markAsPermanentlyFailed(
        id: Long,
        error: String,
    )

    /**
     * Get queue statistics.
     */
    @Query(
        """
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) as pending,
            SUM(CASE WHEN status = 'processing' THEN 1 ELSE 0 END) as processing,
            SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed,
            SUM(CASE WHEN status = 'permanently_failed' THEN 1 ELSE 0 END) as permanentlyFailed,
            SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) as completed
        FROM webhook_queue
    """
    )
    suspend fun getQueueStatistics(): WebhookQueueStatistics

    /**
     * Clean up old completed webhook events.
     */
    @Query(
        """
        DELETE FROM webhook_queue 
        WHERE status IN ("completed", "permanently_failed") 
        AND created_at < :cutoffTime
    """
    )
    suspend fun cleanupOldEntries(cutoffTime: Long)

    /**
     * Recover stuck processing webhooks (timed out workers).
     */
    @Query(
        """
        UPDATE webhook_queue 
        SET status = "pending" 
        WHERE status = "processing" AND next_attempt < :timeoutThreshold
    """
    )
    suspend fun recoverStuckProcessingWebhooks(
        timeoutThreshold: Long = System.currentTimeMillis() - (5 * 60 * 1000) // 5 minutes
    )
}

/**
 * Data class for queue statistics.
 */
data class WebhookQueueStatistics(
    val total: Int,
    val pending: Int,
    val processing: Int,
    val failed: Int,
    val permanentlyFailed: Int,
    val completed: Int
)