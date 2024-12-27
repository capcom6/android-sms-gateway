package me.capcom.smsgateway.modules.receiver.data

import java.util.Date

data class InboxMessage(
    val address: String,
    val body: String,
    val date: Date,
    val subscriptionId: Int?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InboxMessage) return false

        if (address != other.address) return false
        if (body != other.body) return false
        if (date != other.date) return false
        if (subscriptionId != other.subscriptionId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + body.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + (subscriptionId ?: 0)
        return result
    }
}
