package com.example.model.api

data class UpdateAgentConfigRequest(
    val name: String?,
    val dailySmsLimit: Int?,
    val smsPrefix: String?
)

data class SubmitSmsRequest(
    val recipient: String,
    val messageContent: String,
    val agentId: String? = null // Optional: for preferred agent or direct assignment
)
