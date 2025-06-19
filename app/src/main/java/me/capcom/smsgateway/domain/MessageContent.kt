package me.capcom.smsgateway.domain

sealed class MessageContent {
    data class Text(val text: String) : MessageContent() {
        override fun toString(): String {
            return text
        }
    }

    data class Data(val data: String, val port: Short) : MessageContent() {
        override fun toString(): String {
            return "$data:$port"
        }
    }
}