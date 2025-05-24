package me.capcom.smsgateway.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class OutgoingSmsStatus {
    PENDING,
    SENT, // Successfully sent to server
    FAILED, // Failed to send to server or server reported failure
    CONFIRMED_DELIVERED, // (Optional) If server can confirm final delivery
    CONFIRMED_FAILED     // (Optional) If server can confirm final failure
}

@Entity(
    tableName = "queued_outgoing_sms",
    indices = [
        Index(value = ["status", "nextSendAttemptAt"])
    ]
)
data class QueuedOutgoingSms(
    @PrimaryKey val taskId: String = UUID.randomUUID().toString(),
    val recipient: String,
    val messageContent: String,
    @ColumnInfo(defaultValue = "PENDING")
    var status: OutgoingSmsStatus = OutgoingSmsStatus.PENDING,
    @ColumnInfo(defaultValue = "0")
    var retries: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    var nextSendAttemptAt: Long = System.currentTimeMillis()
)
