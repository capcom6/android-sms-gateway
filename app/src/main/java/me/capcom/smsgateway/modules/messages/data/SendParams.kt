package me.capcom.smsgateway.modules.messages.data

class SendParams(
    val withDeliveryReport: Boolean,
    val skipPhoneValidation: Boolean,
    /**
     * Sim number (from 1 to 3)
     */
    val simNumber: Int? = null,
)