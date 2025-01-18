package me.capcom.smsgateway.modules.ping.events

import me.capcom.smsgateway.domain.HealthResponse
import me.capcom.smsgateway.modules.events.AppEvent

class PingEvent(
    val health: HealthResponse,
) : AppEvent(TYPE) {
    companion object {
        const val TYPE = "PingEvent"
    }
}