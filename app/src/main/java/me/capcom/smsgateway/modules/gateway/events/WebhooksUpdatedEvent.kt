package me.capcom.smsgateway.modules.gateway.events

import me.capcom.smsgateway.modules.events.AppEvent

class WebhooksUpdatedEvent : AppEvent(NAME) {
    companion object {
        const val NAME = "WebhooksUpdatedEvent"
    }
}