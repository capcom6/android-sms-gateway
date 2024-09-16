package me.capcom.smsgateway.modules.webhooks.payload

import java.util.Date

sealed class SmsEventPayload(
    val messageId: String,
    val phoneNumber: String,
) {
    class SmsSent(
        messageId: String,
        phoneNumber: String,
        val sentAt: Date
    ) : SmsEventPayload(messageId, phoneNumber)

    class SmsDelivered(
        messageId: String,
        phoneNumber: String,
        val deliveredAt: Date
    ) : SmsEventPayload(messageId, phoneNumber)

    class SmsFailed(
        messageId: String,
        phoneNumber: String,
        val failedAt: Date,
        val reason: String,
    ) : SmsEventPayload(messageId, phoneNumber)
}