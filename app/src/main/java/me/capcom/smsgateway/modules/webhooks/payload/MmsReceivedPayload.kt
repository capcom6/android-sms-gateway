package me.capcom.smsgateway.modules.webhooks.payload

import java.util.Date

class MmsReceivedPayload(
    messageId: String,
    phoneNumber: String,
    simNumber: Int?,
    val transactionId: String,
    val subject: String?,
    val size: Long,
    val contentClass: String?,
    val receivedAt: Date
) : MessageEventPayload(messageId, phoneNumber, simNumber)