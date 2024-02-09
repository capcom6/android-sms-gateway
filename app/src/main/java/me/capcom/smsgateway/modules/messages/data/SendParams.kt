package me.capcom.smsgateway.modules.messages.data

import java.util.Date

class SendParams(
    val withDeliveryReport: Boolean,
    val simNumber: Int?,
    val validUntil: Date?,
)