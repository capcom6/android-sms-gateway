package me.capcom.smsgateway.modules.localserver.domain

import me.capcom.smsgateway.domain.MessageState

data class PostMessageResponse(
    val id: String,
    val state: MessageState,
    val recipients: List<Recipient>,
) {

    data class Recipient(
        val phoneNumber: String,
        val state: MessageState,
        val error: String?
    )
}
