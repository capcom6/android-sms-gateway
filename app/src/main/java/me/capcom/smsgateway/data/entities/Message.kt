package me.capcom.smsgateway.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Message(
    @PrimaryKey val id: String,
    val text: String,
    val recipients: List<String>,
    val state: State = State.Pending,
) {
    enum class State {
        Pending,
        Sent,
        Delivered,
        Failed,
    }
}