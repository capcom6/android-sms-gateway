package me.capcom.smsgateway.modules.messages.data

import me.capcom.smsgateway.domain.MessageContent
import java.util.Date

data class Message(
    val id: String,
    val content: MessageContent,
    val phoneNumbers: List<String>,

    val isEncrypted: Boolean,

    val createdAt: Date,
)