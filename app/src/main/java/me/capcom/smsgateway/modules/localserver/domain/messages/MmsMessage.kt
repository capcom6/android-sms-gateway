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
    ) {
        fun validate(index: Int) {
            if (contentType.isBlank()) {
                throw IllegalArgumentException("Attachment $index: contentType is required")
            }
            if (data.isNullOrBlank() && url.isNullOrBlank()) {
                throw IllegalArgumentException("Attachment $index: must provide data or url")
            }
            if (!data.isNullOrBlank() && !url.isNullOrBlank()) {
                throw IllegalArgumentException("Attachment $index: data and url are mutually exclusive")
            }
        }
    }
}
