package me.capcom.smsgateway.data.remote.dto

// Note: UUIDs from server will be handled as Strings on the client side for simplicity with Gson.
// If strong UUID typing is needed, custom TypeAdapters for Gson would be required.

data class RegisterAgentRequest(val name: String)

data class RegisterAgentResponse(
    val id: String, // UUID as String
    val apiKey: String,
    val name: String
)

data class AgentConfigResponse(
    val isEnabled: Boolean,
    val dailySmsLimit: Int,
    val smsPrefix: String?, // Keep as nullable
    val name: String
)

data class AgentSmsTaskResponse(
    val taskId: String, // UUID as String
    val recipient: String,
    val messageText: String
)

data class TaskStatusUpdateRequest(
    val status: String, // "SENT" or "FAILED"
    val failureReason: String? = null
)

data class IncomingSmsRequest(
    val sender: String,
    val messageText: String,
    val timestamp: Long // Milliseconds since epoch
)
