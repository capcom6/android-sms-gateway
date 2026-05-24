package me.capcom.smsgateway.modules.localserver.domain.messages

data class MmsMessage(
    val subject: String? = null,
    val text: String? = null,
    val attachments: List<Attachment> = emptyList(),
) {
    data class Attachment(
        val contentType: String,
        val name: String? = null,
        val data: String? = null,
        val url: String? = null,
    )
}
