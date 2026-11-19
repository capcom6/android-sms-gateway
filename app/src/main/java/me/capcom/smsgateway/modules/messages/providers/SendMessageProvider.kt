package me.capcom.smsgateway.modules.messages.providers

import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest

interface SendMessageProvider {
    fun supports(content: MessageContent): Boolean

    suspend fun send(request: StoredSendRequest)
}
