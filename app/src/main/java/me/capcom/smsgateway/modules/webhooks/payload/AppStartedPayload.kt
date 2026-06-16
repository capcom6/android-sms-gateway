package me.capcom.smsgateway.modules.webhooks.payload

import me.capcom.smsgateway.modules.localserver.domain.SimCard

data class AppStartedPayload(
    val simCards: List<SimCard>,
)
