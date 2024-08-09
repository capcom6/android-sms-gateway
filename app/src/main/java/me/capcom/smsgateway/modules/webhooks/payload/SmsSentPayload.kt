package me.capcom.smsgateway.modules.webhooks.payload

import java.util.Date

data class SmsSentPayload(
    val messageId: String,
    val phoneNumber: String,
    val sentAt: Date,
)