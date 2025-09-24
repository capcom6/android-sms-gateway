package me.capcom.smsgateway.data.entities

data class MessagesTotals(
    val total: Long,
    val pending: Long,
    val sent: Long,
    val delivered: Long,
    val failed: Long,
)
