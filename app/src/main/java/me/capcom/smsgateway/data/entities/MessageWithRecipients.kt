package me.capcom.smsgateway.data.entities

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
    val state: me.capcom.smsgateway.domain.ProcessingState
        get() = when {
            recipients.any { it.state == me.capcom.smsgateway.domain.ProcessingState.Pending } -> me.capcom.smsgateway.domain.ProcessingState.Pending
            recipients.any { it.state == me.capcom.smsgateway.domain.ProcessingState.Processed } -> me.capcom.smsgateway.domain.ProcessingState.Processed

            recipients.all { it.state == me.capcom.smsgateway.domain.ProcessingState.Failed } -> me.capcom.smsgateway.domain.ProcessingState.Failed
            recipients.all { it.state == me.capcom.smsgateway.domain.ProcessingState.Delivered } -> me.capcom.smsgateway.domain.ProcessingState.Delivered
            else -> me.capcom.smsgateway.domain.ProcessingState.Sent
        }
}
