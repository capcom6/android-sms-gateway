package com.example.model.api

import java.util.UUID
import java.time.Instant

// Agent Registration
data class RegisterAgentRequest(val name: String)
data class RegisterAgentResponse(val id: UUID, val apiKey: String, val name: String)

// Agent Configuration
data class AgentConfigResponse(
    val isEnabled: Boolean,
    val dailySmsLimit: Int,
    val smsPrefix: String?,
    val name: String
)

// Agent Task
data class AgentSmsTaskResponse(
    val taskId: UUID,
    val recipient: String,
    val messageText: String
    // Other relevant fields like createdAt can be added if needed by agent
)

// Agent Task Status Update
data class TaskStatusUpdateRequest(
    val status: String, // e.g., "SENT", "FAILED"
    val failureReason: String? = null,
    val smppMessageId: String? = null // Optional: if agent receives it from its modem/connection
)

// Agent Incoming SMS
data class IncomingSmsRequest(
    val sender: String,
    val messageText: String,
    val timestamp: Long // Milliseconds since epoch, as sent by agent
)
