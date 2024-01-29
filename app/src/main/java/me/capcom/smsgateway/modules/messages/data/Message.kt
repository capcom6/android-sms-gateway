package me.capcom.smsgateway.modules.messages.data

data class Message(
    val id: String,
    val text: String,
    val phoneNumbers: List<String>,
    val isEncrypted: Boolean,
)