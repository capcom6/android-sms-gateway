package me.capcom.smsgateway.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import java.util.Date

enum class MessageType {
    Text,
    Data,
}

@Entity(
    indices = [
        androidx.room.Index(value = ["createdAt"]),
        androidx.room.Index(value = ["state", "processedAt"]),
        androidx.room.Index(value = ["state", "createdAt"]),
        androidx.room.Index(value = ["state", "scheduleAt"]),
    ]
)
data class Message constructor(
    @PrimaryKey val id: String,

    @ColumnInfo(defaultValue = "1")
    val withDeliveryReport: Boolean,
    val simNumber: Int?,
    val validUntil: Date?,
    val scheduleAt: Date?,
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
    constructor(
        id: String,
        withDeliveryReport: Boolean,
        simNumber: Int?,
        validUntil: Date?,
        scheduleAt: Date?,
        isEncrypted: Boolean,
        skipPhoneValidation: Boolean,
        priority: Byte,
        source: EntitySource,

        content: MessageContent,

        createdAt: Long,
    ) : this(
        id,
        withDeliveryReport,
        simNumber,
        validUntil,
        scheduleAt,
        isEncrypted,
        skipPhoneValidation,
        priority,
        source,

        content = gson.toJson(content),
        type = when (content) {
            is MessageContent.Text -> MessageType.Text
            is MessageContent.Data -> MessageType.Data
        },
        createdAt = createdAt,
    )

    val textContent: MessageContent.Text?
        get() = when (type) {
            MessageType.Text -> gson.fromJson(content, MessageContent.Text::class.java)
            else -> null
        }

    val dataContent: MessageContent.Data?
        get() = when (type) {
            MessageType.Data -> gson.fromJson(content, MessageContent.Data::class.java)
            else -> null
        }

    companion object {
        const val PRIORITY_MIN: Byte = Byte.MIN_VALUE
        const val PRIORITY_DEFAULT: Byte = 0
        const val PRIORITY_EXPEDITED: Byte = 100

        private val gson = Gson()
    }
}
