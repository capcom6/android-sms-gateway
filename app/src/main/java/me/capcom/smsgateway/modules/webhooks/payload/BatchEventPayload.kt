package me.capcom.smsgateway.modules.webhooks.payload

abstract class BatchEventPayload<T : MessageEventPayload>(
    val messages: List<T>,
)