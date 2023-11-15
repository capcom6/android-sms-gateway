package me.capcom.smsgateway.modules.localserver.domain

data class PostMessageRequest(
    val id: String?,
    val message: String,
    val phoneNumbers: List<String>,
    val simNumber: Int? = null,
)