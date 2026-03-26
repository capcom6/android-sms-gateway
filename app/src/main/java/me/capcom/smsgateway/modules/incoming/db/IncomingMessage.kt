package me.capcom.smsgateway.modules.incoming.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class IncomingMessageType {
    SMS,
    DATA_SMS,
    MMS,
    MMS_DOWNLOADED,
}

@Entity(
    tableName = "incoming_messages",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["type"]),
    ]
)
data class IncomingMessage(
    @PrimaryKey val id: String,
    val type: IncomingMessageType,
    val sender: String,
    val recipient: String?,
    val simNumber: Int?,
    val subscriptionId: Int?,
    val contentPreview: String,
    val createdAt: Long = System.currentTimeMillis(),
)
