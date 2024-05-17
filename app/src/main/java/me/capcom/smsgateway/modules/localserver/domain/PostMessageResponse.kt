package me.capcom.smsgateway.modules.localserver.domain

import me.capcom.smsgateway.domain.MessageState
import java.util.Date

data class PostMessageResponse(
    val id: String,
    val state: MessageState,
    val recipients: List<Recipient>,
    val isEncrypted: Boolean,
    val states: Map<MessageState, Date>
) {

    data class Recipient(
        val phoneNumber: String,
        val state: MessageState,
        val error: String?
    )
}
