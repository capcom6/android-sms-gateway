package me.capcom.smsgateway.modules.messages.data

data class SendRequest(
    val source: MessageSource,
    val message: Message,
    val params: SendParams,
)