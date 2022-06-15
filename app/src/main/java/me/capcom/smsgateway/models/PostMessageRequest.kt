package me.capcom.smsgateway.models

class PostMessageRequest(
    val message: String,
    val phoneNumbers: List<String>
)