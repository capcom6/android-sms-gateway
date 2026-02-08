package me.stappmus.messagegateway.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["messageId", "state"],
    foreignKeys = [
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageState(
    val messageId: String,
    val state: me.stappmus.messagegateway.domain.ProcessingState,
    val updatedAt: Long
)
