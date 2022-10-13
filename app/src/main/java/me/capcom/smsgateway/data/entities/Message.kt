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
) {
    enum class State {
        Pending,
        Sent,
        Delivered,
        Failed,
    }

    enum class Source {
        Local,
        Gateway,
    }
}