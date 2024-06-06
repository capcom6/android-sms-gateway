package me.capcom.smsgateway.modules.webhooks.domain

data class WebHookEventDTO(
    val event: WebHookEvent,
    val payload: Any,
)
