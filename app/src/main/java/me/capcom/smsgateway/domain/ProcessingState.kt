package me.capcom.smsgateway.domain

enum class ProcessingState {
    Pending,
    Cancelled,
    Processed,
    Sent,
    Delivered,
    Failed
}