package me.capcom.smsgateway.modules.ping.events

import me.capcom.smsgateway.modules.events.AppEvent

class PingEvent : AppEvent(TYPE) {
    companion object {
        const val TYPE = "PingEvent"
    }
}