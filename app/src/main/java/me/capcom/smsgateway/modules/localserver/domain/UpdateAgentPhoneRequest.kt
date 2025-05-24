package me.capcom.smsgateway.modules.localserver.domain

data class UpdateAgentPhoneRequest(
    val name: String?,
    val dailySmsLimit: Int?
    // smsPrefix is not part of AgentPhone.
    // isEnabled is handled by a separate endpoint.
    // apiKey should not be updatable via this request.
)
