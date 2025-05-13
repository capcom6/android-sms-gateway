package me.capcom.smsgateway.modules.webhooks.domain

import me.capcom.smsgateway.domain.EntitySource

data class WebHookDTO(
    val id: String?,
    val url: String,
    val event: WebHookEvent,
    val source: EntitySource,
)
