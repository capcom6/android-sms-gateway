package me.capcom.smsgateway.modules.webhooks.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
     * Insert multiple webhook events into the queue.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllWebhooks(webhooks: List<WebhookQueueEntity>): List<Long>
    
    /**
     * Get all pending webhook events ordered by priority (highest first) and next attempt time.
     */
    @Query("""
        SELECT * FROM webhook_queue 
        WHERE status = :status AND next_attempt <= :currentTime 
        ORDER BY priority DESC, next_attempt ASC 
        LIMIT :limit
    """)
    suspend fun getPendingWebhooks(
        status: WebhookStatus = WebhookStatus.PENDING,
        currentTime: Long = System.currentTimeMillis(),
        limit: Int = 50
    ): List<WebhookQueueEntity>
    
    /**
     * Get pending webhook events by priority level.
     */
    @Query("""
        SELECT * FROM webhook_queue 
        WHERE status = :status AND priority = :priority AND next_attempt <= :currentTime 
        ORDER BY next_attempt ASC 
        LIMIT :limit
    """)
    suspend fun getPendingWebhooksByPriority(
        status: WebhookStatus = WebhookStatus.PENDING,
        priority: WebhookPriority,
        currentTime: Long = System.currentTimeMillis(),
        limit: Int = 50
    ): List<WebhookQueueEntity>
    
    /**
     * Update webhook status.
     */
    @Query("""
        UPDATE webhook_queue 
        SET status = :status 
        WHERE id = :id
    """)
    suspend fun updateStatus(id: Long, status: WebhookStatus)
    
    /**
     * Update multiple webhook statuses by webhook ID.
     */
    @Query("""
        UPDATE webhook_queue 
        SET status = :status 
        WHERE webhook_id = :webhookId AND status = :oldStatus
    """)
    suspend fun updateStatusByWebhookId(
        webhookId: String,
        oldStatus: WebhookStatus,
        newStatus: WebhookStatus
    )
    
    /**
     * Update retry information and set next attempt time.
     */
    @Query("""
        UPDATE webhook_queue 
        SET retry_count = retry_count + 1, 
            next_attempt = :nextAttempt,
            last_error = :error
        WHERE id = :id
    """)
    suspend fun updateRetryInfo(
        id: Long,
        nextAttempt: Long,
        error: String?
    )
    
    /**
     * Mark webhook as completed.
     */
    @Query("""
        UPDATE webhook_queue 
        SET status = :completedStatus 
        WHERE id = :id AND status = :oldStatus
    """)
    suspend fun markAsCompleted(
        id: Long,
        oldStatus: WebhookStatus,
        completedStatus: WebhookStatus = WebhookStatus.COMPLETED
    )
    
    /**
     * Mark webhook as permanently failed.
     */
    @Query("""
        UPDATE webhook_queue 
        SET status = :failedStatus, last_error = :error
        WHERE id = :id
    """)
    suspend fun markAsPermanentlyFailed(
        id: Long,
        error: String,
        failedStatus: WebhookStatus = WebhookStatus.PERMANENTLY_FAILED
    )
    
    /**
     * Get queue statistics.
     */
    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) as pending,
            SUM(CASE WHEN status = 'processing' THEN 1 ELSE 0 END) as processing,
            SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed,
            SUM(CASE WHEN status = 'permanently_failed' THEN 1 ELSE 0 END) as permanentlyFailed,
            SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) as completed
        FROM webhook_queue
    """)
    suspend fun getQueueStatistics(): WebhookQueueStatistics
    
    /**
     * Get all webhook queue items for debugging (not for production use).
     */
    @Query("SELECT * FROM webhook_queue ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentWebhooks(limit: Int = 100): List<WebhookQueueEntity>
    
    /**
     * Clean up old completed webhook events.
     */
    @Query("""
        DELETE FROM webhook_queue 
        WHERE status IN ('completed', 'permanently_failed') 
        AND created_at < :cutoffTime
    """)
    suspend fun cleanupOldEntries(cutoffTime: Long)
    
    /**
     * Get processing webhooks older than threshold.
     */
    @Query("""
        SELECT * FROM webhook_queue 
        WHERE status = :processingStatus AND next_attempt < :timeoutThreshold
        ORDER BY next_attempt ASC
    """)
    suspend fun getStuckProcessingWebhooks(
        processingStatus: WebhookStatus = WebhookStatus.PROCESSING,
        timeoutThreshold: Long = System.currentTimeMillis() - (5 * 60 * 1000) // 5 minutes
    ): List<WebhookQueueEntity>
    
    /**
     * Count pending webhooks for a specific webhook ID.
     */
    @Query("""
        SELECT COUNT(*) FROM webhook_queue 
        WHERE webhook_id = :webhookId AND status IN ('pending', 'processing')
    """)
    suspend fun getPendingCountForWebhook(webhookId: String): Int
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