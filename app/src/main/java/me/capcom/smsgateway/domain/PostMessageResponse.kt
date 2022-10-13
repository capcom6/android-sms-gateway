package me.capcom.smsgateway.domain

data class PostMessageResponse(
    val id: String,
    val state: MessageState,
    val recipients: List<Recipient>,
) {

    data class Recipient(
        val phoneNumber: String,
        val state: MessageState
    )
}
