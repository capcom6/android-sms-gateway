package me.stappmus.messagegateway.modules.webhooks.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Entity representing a webhook event in the processing queue.
 * This is used for queue-based webhook processing with retry logic.
 */
@Entity(
    tableName = "webhook_queue",
    indices = [
        androidx.room.Index(value = ["status", "next_attempt"]),
        androidx.room.Index(value = ["status", "created_at"]),
    ]
)
@TypeConverters(WebhookConverters::class)
data class WebhookQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    val payload: String,

    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,

    @ColumnInfo(defaultValue = "pending")
    val status: WebhookStatus = WebhookStatus.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "next_attempt")
    val nextAttempt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
)