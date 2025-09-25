package me.capcom.smsgateway.modules.messages.events

import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.events.AppEvent

class MessageStateChangedEvent(
    val id: String,
    val source: EntitySource,
    val phoneNumbers: Set<String>,
    val state: ProcessingState,
    val simNumber: Int?,
    val partsCount: Int?,
    val error: String?
): AppEvent(NAME) {

    companion object {
        const val NAME = "MessageStateChangedEvent"
    }
}