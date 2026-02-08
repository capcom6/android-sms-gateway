package me.stappmus.messagegateway.modules.gateway.events

import me.stappmus.messagegateway.modules.events.AppEvent

class WebhooksUpdatedEvent : AppEvent(NAME) {
    companion object {
        const val NAME = "WebhooksUpdatedEvent"
    }
}