package me.capcom.smsgateway.modules.receiver.events

import me.capcom.smsgateway.modules.events.AppEvent
import java.util.Date

class MessageReceivedEvent(
    val message: String,
    val phoneNumber: String,
    val receivedAt: Date,
    val simNumber: Int?,
) : AppEvent(
    name = NAME
) {
    companion object {
        const val NAME = "MessageReceivedEvent"
    }
}
