package me.capcom.smsgateway.modules.messages.events

import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.modules.events.AppEvent
import me.capcom.smsgateway.modules.messages.data.MessageSource

class MessageStateChangedEvent(
    val id: String,
    val state: Message.State,
    val source: MessageSource,
    val recipients: List<Recipient>,
): AppEvent(NAME) {
    companion object {
        const val NAME = "MessageStateChangedEvent"
    }

    data class Recipient(
        val phoneNumber: String,
        val state: Message.State,
        val error: String?,
    )
}