package me.stappmus.messagegateway.domain

enum class ProcessingState {
    Pending,
    Processed,
    Sent,
    Delivered,
    Failed
}