package com.example.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object RegisteredAgentsTable : UUIDTable("registered_agents") {
    val name = varchar("name", 255)
    val apiKeyHash = varchar("api_key_hash", 255).uniqueIndex()
    val isEnabled = bool("is_enabled").default(true)
    val dailySmsLimit = integer("daily_sms_limit").default(-1) // -1 for no limit
    val currentDailySmsCount = integer("current_daily_sms_count").default(0)
    val lastSmsCountResetAt = timestamp("last_sms_count_reset_at").default(Instant.now())
    val lastHeartbeatAt = timestamp("last_heartbeat_at").nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())
    val smsPrefix = varchar("sms_prefix", 50).nullable() // Added smsPrefix
}

object OutgoingSmsQueueTable : UUIDTable("outgoing_sms_queue") {
    val agentId = uuid("agent_id").references(RegisteredAgentsTable.id).nullable()
    val messageContent = text("message_content")
    val recipient = varchar("recipient", 50) // Assuming a reasonable length for phone numbers
    val status = varchar("status", 20).default("PENDING") // PENDING, ASSIGNED, SENT, FAILED, RETRY
    val retries = integer("retries").default(0)
    val maxRetries = integer("max_retries").default(3)
    val failureReason = text("failure_reason").nullable()
    val receivedFromSmppAt = timestamp("received_from_smpp_at").nullable() // When gateway received it from SMPP
    val assignedToAgentAt = timestamp("assigned_to_agent_at").nullable()
    val sentByAgentAt = timestamp("sent_by_agent_at").nullable() // Reported by agent
    val completedAt = timestamp("completed_at").nullable() // When processing finished (SENT or FAILED after retries)
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())
    // TODO: Consider adding source (e.g., "SMPP", "API") if needed for routing or analytics
}

object IncomingSmsLogTable : UUIDTable("incoming_sms_log") {
    val agentId = uuid("agent_id").references(RegisteredAgentsTable.id)
    val originalSender = varchar("original_sender", 50) // Phone number of the original sender
    val messageContent = text("message_content")
    val receivedAtAgentAt = timestamp("received_at_agent_at") // Timestamp from agent
    val createdAt = timestamp("created_at").default(Instant.now()) // When gateway logged it
}
