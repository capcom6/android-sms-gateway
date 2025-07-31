package me.capcom.smsgateway.modules.events

data class ExternalEvent(
    val type: ExternalEventType,
    val data: String?,
)
