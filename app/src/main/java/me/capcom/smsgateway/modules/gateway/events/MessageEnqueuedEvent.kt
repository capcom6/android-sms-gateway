package me.capcom.smsgateway.modules.gateway.events

import me.capcom.smsgateway.modules.events.AppEvent

class MessageEnqueuedEvent : AppEvent(NAME) {
    companion object {
        const val NAME = "MessageEnqueuedEvent"
    }
}