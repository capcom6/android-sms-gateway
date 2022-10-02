package me.capcom.smsgateway.domain

data class PostMessageResponse(
    val id: String,
    val state: State,
    val recipients: List<Recipient>,
) {
    enum class State {
        Pending,
        Sent,
        Delivered,
        Failed
    }

    data class Recipient(
        val phoneNumber: String,
        val state: State
    )
}
