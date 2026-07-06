package me.capcom.smsgateway.domain

enum class ProcessingState {
    Pending,
    Cancelling,
    Cancelled,
    Processed,
    Sent,
    Delivered,
    Failed
}