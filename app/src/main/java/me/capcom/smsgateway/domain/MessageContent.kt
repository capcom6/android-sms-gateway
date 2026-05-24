package me.capcom.smsgateway.domain

sealed class MessageContent {
    data class Text(val text: String) : MessageContent() {
        override fun toString(): String {
            return text
        }
    }

    data class Data(val data: String, val port: UShort) : MessageContent() {
        override fun toString(): String {
            return "$data:$port"
        }
    }

    data class Mms(
        val subject: String?,
        val text: String?,
        val attachments: List<Attachment>,
    ) : MessageContent() {
        data class Attachment(
            val contentType: String,
            val name: String?,
            val data: String?,
            val url: String?,
        )

        override fun toString(): String {
            return "mms(subject=$subject, text.len=${text?.length ?: 0}, attachments=${attachments.size})"
        }
    }
}
