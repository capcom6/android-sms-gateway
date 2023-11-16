package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.modules.events.AppEvent

class MessageStateChangedEvent(
    val id: String,
    val state: Message.State,
    val source: Message.Source,
    val recipients: Map<String, Message.State>,
): AppEvent(NAME) {
    companion object {
        const val NAME = "MessageStateChangedEvent"
    }
}