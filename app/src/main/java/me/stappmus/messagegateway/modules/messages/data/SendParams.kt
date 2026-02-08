package me.stappmus.messagegateway.modules.messages.data

import java.util.Date

class SendParams(
    val withDeliveryReport: Boolean,
    val skipPhoneValidation: Boolean,
    /**
     * Sim number (from 1 to 3)
     */
    val simNumber: Int?,
    val validUntil: Date?,
    val priority: Byte?,
)