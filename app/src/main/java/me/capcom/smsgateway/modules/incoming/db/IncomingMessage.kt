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
    // Set by the A4 cloud upload flow once this row has been uploaded. Null until then.
    val uploadedAt: Long? = null,
    // Provider _id used ONLY for content re-read in A4. Never an id, never ext_id.
    val providerId: Long? = null,
)
