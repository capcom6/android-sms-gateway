package me.capcom.smsgateway.modules.webhooks.payload

import java.util.Date

class MmsReceivedPayload(
    messageId: String,
    phoneNumber: String,
    simNumber: Int?,
    val transactionId: String,
    val subject: String?,
    val attachmentCount: Int,
    val size: Long,
    val receivedAt: Date
) : MessageEventPayload(messageId, phoneNumber, simNumber)