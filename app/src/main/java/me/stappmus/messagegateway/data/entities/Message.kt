package me.stappmus.messagegateway.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.domain.ProcessingState
import java.util.Date

enum class MessageType {
    Text,
    Data,
    Mms,
}

@Entity(
    indices = [
        androidx.room.Index(value = ["createdAt"]),
        androidx.room.Index(value = ["state", "processedAt"]),
        androidx.room.Index(value = ["state", "createdAt"]),
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
    val partsCount: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null,
) {
    companion object {
        const val PRIORITY_MIN: Byte = Byte.MIN_VALUE
        const val PRIORITY_DEFAULT: Byte = 0
        const val PRIORITY_EXPEDITED: Byte = 100
    }
}
