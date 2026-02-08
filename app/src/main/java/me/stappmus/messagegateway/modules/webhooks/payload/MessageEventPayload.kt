package me.stappmus.messagegateway.modules.webhooks.payload

abstract class MessageEventPayload(
    val messageId: String,
    val phoneNumber: String,
    val simNumber: Int?,
)