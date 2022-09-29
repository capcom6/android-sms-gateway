package me.capcom.smsgateway.domain

data class PostMessageRequest(
    val message: String,
    val phoneNumbers: List<String>
)