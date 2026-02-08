package me.stappmus.messagegateway.modules.ping.events

import me.stappmus.messagegateway.domain.HealthResponse
import me.stappmus.messagegateway.modules.events.AppEvent

class PingEvent(
    val health: HealthResponse,
) : AppEvent(TYPE) {
    companion object {
        const val TYPE = "PingEvent"
    }
}