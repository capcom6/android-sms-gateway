package me.stappmus.messagegateway.modules.messages.data

import me.stappmus.messagegateway.data.entities.MessageRecipient
import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.domain.ProcessingState

class StoredSendRequest(
    val id: Long,
    val state: ProcessingState,
    val recipients: List<MessageRecipient>,
    source: EntitySource,
    message: Message,
    params: SendParams
) :
    SendRequest(source, message, params)