package me.stappmus.messagegateway.modules.webhooks.payload

import me.stappmus.messagegateway.domain.MmsAttachment
import java.util.Date

class MmsReceivedPayload(
    messageId: String,
    phoneNumber: String,
    simNumber: Int?,
    val transactionId: String,
    val subject: String?,
    val size: Long,
    val contentClass: String?,
    val attachments: List<MmsAttachment> = emptyList(),
    val receivedAt: Date
) : MessageEventPayload(messageId, phoneNumber, simNumber)
