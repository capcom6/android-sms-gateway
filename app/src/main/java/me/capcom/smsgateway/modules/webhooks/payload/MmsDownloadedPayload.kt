package me.capcom.smsgateway.modules.webhooks.payload

import java.util.Date

class MmsDownloadedPayload(
    messageId: String,
    sender: String?,
    recipient: String?,
    simNumber: Int?,
    val body: String?,
    val subject: String?,
    val attachments: List<Attachment>,
    val receivedAt: Date,
) : MessageEventPayload(messageId, sender, recipient, simNumber, sender ?: "") {

    class Attachment(
        val partId: Long,
        val contentType: String,
        val name: String?,
        val size: Long,
        val data: String?,
    )
}
