package me.capcom.smsgateway.modules.localserver.domain

data class CreateAgentPhoneRequest(
    val name: String,
    val dailySmsLimit: Int? = -1 // Default to -1 (no limit) if not provided
    // smsPrefix is not part of AgentPhone, it's part of VirtualPhoneConfig which is being replaced.
    // If smsPrefix is needed for Agents, it should be added to AgentPhone entity.
    // For now, assuming it's not part of the AgentPhone concept.
)
