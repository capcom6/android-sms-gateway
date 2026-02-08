package me.stappmus.messagegateway.domain

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
        val text: String?,
        val attachments: List<MmsAttachment>,
    ) : MessageContent() {
        override fun toString(): String {
            val mediaCount = attachments.size
            return listOfNotNull(text?.takeIf { it.isNotBlank() }, "attachments=$mediaCount")
                .joinToString(" ")
                .ifBlank { "attachments=$mediaCount" }
        }
    }
}
