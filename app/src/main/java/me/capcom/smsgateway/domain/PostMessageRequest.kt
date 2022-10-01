package me.capcom.smsgateway.domain

data class PostMessageRequest(
    val id: String?,
    val message: String,
    val phoneNumbers: List<String>
)