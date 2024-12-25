package me.capcom.smsgateway.modules.receiver.data

import java.util.Date

data class InboxMessage(
    val id: Long?,
    val address: String,
    val body: String,
    val date: Date,
    val subscriptionId: Int?
)
