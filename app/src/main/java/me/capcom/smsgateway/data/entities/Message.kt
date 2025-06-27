package me.capcom.smsgateway.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.ProcessingState
import java.util.Date

enum class MessageType {
    Text,
    Data,
}

@Entity(
    indices = [
        androidx.room.Index(value = ["state"]),
        androidx.room.Index(value = ["createdAt"]),
        androidx.room.Index(value = ["processedAt"]),
    ]
)
data class Message(
    @PrimaryKey val id: String,

    @ColumnInfo(defaultValue = "1")
    val withDeliveryReport: Boolean,
    val simNumber: Int?,
    val validUntil: Date?,
    @ColumnInfo(defaultValue = "0")
    val isEncrypted: Boolean,
    @ColumnInfo(defaultValue = "0")
    val skipPhoneValidation: Boolean,
    @ColumnInfo(defaultValue = "0")
    val priority: Byte,

    @ColumnInfo(defaultValue = "Local")
    val source: EntitySource,

    @ColumnInfo(defaultValue = "Text")
    val type: MessageType = MessageType.Text,

    val content: String,

    val state: ProcessingState = ProcessingState.Pending,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null,
) {
    companion object {
        const val PRIORITY_DEFAULT: Byte = 0
        const val PRIORITY_EXPEDITED: Byte = 100
    }
}
