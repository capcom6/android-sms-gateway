package me.stappmus.messagegateway.data.entities

import androidx.room.ColumnInfo
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
    val states: List<MessageState> = emptyList(),
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
) {
    val state: me.stappmus.messagegateway.domain.ProcessingState
        get() = when {
            recipients.any { it.state == me.stappmus.messagegateway.domain.ProcessingState.Pending } -> me.stappmus.messagegateway.domain.ProcessingState.Pending
            recipients.any { it.state == me.stappmus.messagegateway.domain.ProcessingState.Processed } -> me.stappmus.messagegateway.domain.ProcessingState.Processed

            recipients.all { it.state == me.stappmus.messagegateway.domain.ProcessingState.Failed } -> me.stappmus.messagegateway.domain.ProcessingState.Failed
            recipients.all { it.state == me.stappmus.messagegateway.domain.ProcessingState.Delivered } -> me.stappmus.messagegateway.domain.ProcessingState.Delivered
            else -> me.stappmus.messagegateway.domain.ProcessingState.Sent
        }
}
