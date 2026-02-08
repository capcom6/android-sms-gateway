package me.stappmus.messagegateway.modules.gateway.events

import me.stappmus.messagegateway.modules.events.AppEvent

class MessageEnqueuedEvent : AppEvent(NAME) {
    companion object {
        const val NAME = "MessageEnqueuedEvent"
    }
}