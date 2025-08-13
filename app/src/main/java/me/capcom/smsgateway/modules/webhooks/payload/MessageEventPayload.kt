package me.capcom.smsgateway.modules.webhooks.payload

abstract class MessageEventPayload(
    val messageId: String,
    val phoneNumber: String,
    val simNumber: Int?,
)