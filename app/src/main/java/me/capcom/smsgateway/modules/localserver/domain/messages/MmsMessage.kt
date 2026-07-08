package me.capcom.smsgateway.modules.localserver.domain.messages

data class MmsMessage(
    val subject: String? = null,
    val text: String? = null,
    val attachments: List<Attachment> = emptyList(),
) {
    data class Attachment(
        val contentType: String,
        val data: String,
        val name: String? = null,
    ) {
        fun validate(index: Int) {
            if (contentType.isBlank()) {
                throw IllegalArgumentException("Attachment $index: contentType is required")
            }
            if (data.isBlank()) {
                throw IllegalArgumentException("Attachment $index: must provide data")
            }
        }
    }
}
