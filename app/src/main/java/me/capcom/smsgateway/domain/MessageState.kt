package me.capcom.smsgateway.domain

enum class MessageState {
    Pending,
    Processed,
    Sent,
    Delivered,
    Failed
}