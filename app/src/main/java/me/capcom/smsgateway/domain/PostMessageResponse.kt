package me.capcom.smsgateway.domain

data class PostMessageResponse(
    val id: String,
    val state: State
) {
    enum class State {
        Pending,
        Sent,
        Delivered,
        Failed
    }
}
