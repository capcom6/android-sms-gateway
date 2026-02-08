package me.stappmus.messagegateway.modules.webhooks.domain

import me.stappmus.messagegateway.domain.EntitySource

data class WebHookDTO(
    val id: String?,
    val deviceId: String?,
    val url: String,
    val event: WebHookEvent,
    val source: EntitySource,
)
