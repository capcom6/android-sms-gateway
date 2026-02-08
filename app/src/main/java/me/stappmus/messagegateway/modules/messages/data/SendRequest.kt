package me.stappmus.messagegateway.modules.messages.data

import me.stappmus.messagegateway.domain.EntitySource

open class SendRequest(
    val source: EntitySource,
    val message: Message,
    val params: SendParams,
)