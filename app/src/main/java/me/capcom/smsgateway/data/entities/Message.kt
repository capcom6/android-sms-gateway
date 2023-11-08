package me.capcom.smsgateway.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Message(
    @PrimaryKey val id: String,
    val text: String,
    @ColumnInfo(defaultValue = "Local")
    val source: Source,
    val state: State = State.Pending,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
) {
    enum class State {
        Pending,
        Processed,
        Sent,
        Delivered,
        Failed,
    }

    enum class Source {
        Local,
        Gateway,
    }
}