package me.stappmus.messagegateway.data.entities

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
    val state: me.stappmus.messagegateway.domain.ProcessingState,
    val updatedAt: Long
)
