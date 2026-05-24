package me.capcom.smsgateway.modules.webhooks.payload

import java.util.Date

class MmsDownloadedPayload(
    messageId: String,
    sender: String,
    recipient: String?,
    simNumber: Int?,
    val body: String?,
    val subject: String?,
    val attachments: List<Attachment>,
    val receivedAt: Date,
) : MessageEventPayload(messageId, sender, recipient, simNumber, sender) {
    class Attachment(
        val partId: Long,
        val contentType: String,
        val name: String?,
        val size: Long?,
        val data: String?,
        /**
         * Relative path on the local HTTP server that serves these bytes,
         * e.g. `/inbox/<messageId>/attachments/<partId>`. Consumers that
         * prefer not to receive base64-encoded payloads can fetch this URL
         * with the same credentials as the rest of the API.
         */
        val url: String?,
    )
}
