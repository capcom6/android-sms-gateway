package me.capcom.smsgateway.modules.messages.data

import me.capcom.smsgateway.domain.EntitySource

open class SendRequest(
    val source: EntitySource,
    val message: Message,
    val params: SendParams,
)