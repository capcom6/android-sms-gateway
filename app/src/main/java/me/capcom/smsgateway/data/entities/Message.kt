package me.capcom.smsgateway.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.capcom.smsgateway.domain.EntitySource
import java.util.Date

@Entity(indices = [androidx.room.Index(value = ["createdAt"]), androidx.room.Index(value = ["processedAt"])])
data class Message(
    @PrimaryKey val id: String,
    val text: String,
    @ColumnInfo(defaultValue = "1")
    val withDeliveryReport: Boolean,
    val simNumber: Int?,
    val validUntil: Date?,
    @ColumnInfo(defaultValue = "0")
    val isEncrypted: Boolean,
    @ColumnInfo(defaultValue = "0")
    val skipPhoneValidation: Boolean,

    @ColumnInfo(defaultValue = "Local")
    val source: EntitySource,

    val state: State = State.Pending,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null,
) {
    enum class State {
        Pending,
        Processed,
        Sent,
        Delivered,
        Failed,
    }
}
