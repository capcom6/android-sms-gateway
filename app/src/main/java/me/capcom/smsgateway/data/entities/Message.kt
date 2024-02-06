package me.capcom.smsgateway.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.capcom.smsgateway.modules.messages.data.MessageSource

@Entity
data class Message(
    @PrimaryKey val id: String,
    val text: String,
    @ColumnInfo(defaultValue = "Local")
    val source: MessageSource,
    @ColumnInfo(defaultValue = "0")
    val isEncrypted: Boolean,
    val state: State = State.Pending,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),   // do we need index here for querying in UI?
) {
    enum class State {
        Pending,
        Processed,
        Sent,
        Delivered,
        Failed,
    }


}