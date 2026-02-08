package me.stappmus.messagegateway.modules.messages.events

import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.domain.ProcessingState
import me.stappmus.messagegateway.modules.events.AppEvent

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