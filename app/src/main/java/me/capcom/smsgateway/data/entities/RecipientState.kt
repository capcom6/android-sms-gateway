package me.capcom.smsgateway.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["messageId", "phoneNumber", "state"],
    foreignKeys = [
        ForeignKey(
            entity = MessageRecipient::class,
            parentColumns = ["messageId", "phoneNumber"],
            childColumns = ["messageId", "phoneNumber"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecipientState(
    val messageId: String,
    val phoneNumber: String,
    val state: Message.State,
    val updatedAt: Long
)
