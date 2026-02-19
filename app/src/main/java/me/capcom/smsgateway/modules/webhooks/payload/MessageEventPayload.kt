package me.capcom.smsgateway.modules.webhooks.payload

abstract class MessageEventPayload(
    val messageId: String,
    val sender: String?,
    val recipient: String?,
    val simNumber: Int?,

    @Deprecated("Use sender or recipient instead", replaceWith = ReplaceWith("recipient"))
    val phoneNumber: String,
)
