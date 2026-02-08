package me.stappmus.messagegateway.modules.receiver.data

import java.util.Arrays
import java.util.Date
import me.stappmus.messagegateway.domain.MmsAttachment

sealed class InboxMessage(
    val address: String,
    val date: Date,
    val subscriptionId: Int?
) {
    class Text(val text: String, address: String, date: Date, subscriptionId: Int?) :
        InboxMessage(address, date, subscriptionId) {
        override fun equals(other: Any?): Boolean {
            return super.equals(other) && (other as? Text)?.text == this.text
        }

        override fun hashCode(): Int {
            return 31 * super.hashCode() + text.hashCode()
        }
    }

    class Data(val data: ByteArray?, address: String, date: Date, subscriptionId: Int?) :
        InboxMessage(address, date, subscriptionId) {
        override fun equals(other: Any?): Boolean {
            return super.equals(other) && Arrays.equals((other as? Data)?.data, this.data)
        }

        override fun hashCode(): Int {
            return 31 * super.hashCode() + (data?.contentHashCode() ?: 0)
        }
    }

    class Mms(
        val messageId: String?,
        val transactionId: String,
        val subject: String?,
        val size: Long,
        val contentClass: String?,
        val attachments: List<MmsAttachment> = emptyList(),
        address: String,
        date: Date,
        subscriptionId: Int?
    ) : InboxMessage(address, date, subscriptionId) {
        override fun equals(other: Any?): Boolean {
            return other is Mms && other.transactionId == this.transactionId
        }

        override fun hashCode(): Int {
            return transactionId.hashCode()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InboxMessage) return false

        if (address != other.address) return false
        if (date != other.date) return false
        return subscriptionId == other.subscriptionId
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + subscriptionId.hashCode()
        return result
    }
}
