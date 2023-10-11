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
) {
    val state: Message.State
        get() = when {
            recipients.all { it.state == Message.State.Failed } -> Message.State.Failed
            recipients.all { it.state == Message.State.Delivered } -> Message.State.Delivered
            recipients.all { it.state == Message.State.Sent } -> Message.State.Sent
            else -> Message.State.Pending
        }
}
