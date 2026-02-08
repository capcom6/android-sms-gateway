package me.stappmus.messagegateway.modules.events

data class ExternalEvent(
    val type: ExternalEventType,
    val data: String?,
)
