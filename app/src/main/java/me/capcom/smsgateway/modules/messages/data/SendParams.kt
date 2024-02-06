package me.capcom.smsgateway.modules.messages.data

class SendParams(
    val withDeliveryReport: Boolean,
    val simNumber: Int? = null,
)