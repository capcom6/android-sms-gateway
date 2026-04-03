package me.capcom.smsgateway.modules.localserver.domain.messages

data class DataMessage(
    val data: String,  // Base64-encoded payload
    val port: Int,      // Destination port (0-65535)
)