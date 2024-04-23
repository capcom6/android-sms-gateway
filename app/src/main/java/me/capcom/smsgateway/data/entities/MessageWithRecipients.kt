package me.capcom.smsgateway.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class MessageWithRecipients(
    @Embedded val message: Message,
    @Relation(
        parentColumn = "id",
        entityColumn = "messageId",
    )
    val recipients: List<MessageRecipient>,
    @Relation(
        parentColumn = "id",
        entityColumn = "messageId",
    )
    val states: List<RecipientState> = emptyList()
) {
    val state: Message.State
        get() = when {
            recipients.any { it.state == Message.State.Pending } -> Message.State.Pending
            recipients.any { it.state == Message.State.Processed } -> Message.State.Processed

            recipients.all { it.state == Message.State.Failed } -> Message.State.Failed
            recipients.all { it.state == Message.State.Delivered } -> Message.State.Delivered
            else -> Message.State.Sent
        }
}
