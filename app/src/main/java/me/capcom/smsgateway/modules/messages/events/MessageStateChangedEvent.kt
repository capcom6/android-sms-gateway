package me.capcom.smsgateway.modules.messages.events

import me.capcom.smsgateway.modules.events.AppEvent
import me.capcom.smsgateway.modules.messages.data.MessageSource

class MessageStateChangedEvent(
    val id: String,
    val source: MessageSource,
): AppEvent(NAME) {
    companion object {
        const val NAME = "MessageStateChangedEvent"
    }
}