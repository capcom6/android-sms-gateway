package me.stappmus.messagegateway.modules.webhooks.payload

import java.util.Date

sealed class SmsEventPayload(
    messageId: String,
    phoneNumber: String,
    simNumber: Int?,
) : MessageEventPayload(messageId, phoneNumber, simNumber) {
    class SmsSent(
        messageId: String,
        phoneNumber: String,
        simNumber: Int?,
        val partsCount: Int,
        val sentAt: Date,
    ) : SmsEventPayload(messageId, phoneNumber, simNumber)

    class SmsDelivered(
        messageId: String,
        phoneNumber: String,
        simNumber: Int?,
        val deliveredAt: Date,
    ) : SmsEventPayload(messageId, phoneNumber, simNumber)

    class SmsFailed(
        messageId: String,
        phoneNumber: String,
        simNumber: Int?,
        val failedAt: Date,
        val reason: String,
    ) : SmsEventPayload(messageId, phoneNumber, simNumber)

    class SmsReceived(
        messageId: String,
        phoneNumber: String,
        simNumber: Int?,
        val message: String,
        val receivedAt: Date,
    ) : SmsEventPayload(messageId, phoneNumber, simNumber)

    class SmsDataReceived(
        messageId: String,
        phoneNumber: String,
        simNumber: Int?,
        val data: String,
        val receivedAt: Date,
    ) : SmsEventPayload(messageId, phoneNumber, simNumber)
}