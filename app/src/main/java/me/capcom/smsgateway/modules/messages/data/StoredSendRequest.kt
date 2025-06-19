package me.capcom.smsgateway.modules.messages.data

import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.ProcessingState

class StoredSendRequest(
    val id: Long,
    val state: ProcessingState,
    val recipients: List<MessageRecipient>,
    source: EntitySource,
    message: Message,
    params: SendParams
) :
    SendRequest(source, message, params)