package me.capcom.smsgateway.modules.messages.data

import java.util.Date

data class Message(
    val id: String,
    val text: String,
    val phoneNumbers: List<String>,

    val isEncrypted: Boolean,

    val createdAt: Date,
    val virtualPhoneId: String? = null,
)