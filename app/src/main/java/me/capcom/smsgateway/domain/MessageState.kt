package me.capcom.smsgateway.domain

enum class MessageState {
    Pending,
    Sent,
    Delivered,
    Failed
}