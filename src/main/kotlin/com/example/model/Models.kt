package com.example.model

import java.time.Instant
import java.util.UUID

data class RegisteredAgent(
    val id: UUID,
    val name: String,
    val apiKeyHash: String,
    val isEnabled: Boolean,
    val dailySmsLimit: Int,
    val currentDailySmsCount: Int,
    val lastSmsCountResetAt: Instant,
    val lastHeartbeatAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val smsPrefix: String? // Added smsPrefix
)

data class OutgoingSmsTask(
    val id: UUID,
    val agentId: UUID?,
    val messageContent: String,
    val recipient: String,
    val status: String,
    val retries: Int,
    val maxRetries: Int,
    val failureReason: String?,
    val receivedFromSmppAt: Instant?,
    val assignedToAgentAt: Instant?,
    val sentByAgentAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class IncomingSms(
    val id: UUID,
    val agentId: UUID,
    val originalSender: String,
    val messageContent: String,
    val receivedAtAgentAt: Instant,
    val createdAt: Instant
)
