package me.capcom.smsgateway.modules.receiver.data

import java.util.Arrays
import java.util.Date

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

    class MmsHeaders(
        val messageId: String?,
        val transactionId: String,
        val subject: String?,
        val size: Long,
        val contentClass: String?,
        address: String,
        date: Date,
        subscriptionId: Int?
    ) : InboxMessage(address, date, subscriptionId) {
        override fun equals(other: Any?): Boolean {
            return other is MmsHeaders && other.transactionId == this.transactionId
        }

        override fun hashCode(): Int {
            return transactionId.hashCode()
        }
    }

    class MMS(
        val messageId: String,
        val body: String?,
        val subject: String?,
        val attachments: List<Attachment>,
        address: String,
        date: Date,
        subscriptionId: Int?
    ) : InboxMessage(address, date, subscriptionId) {
        class Attachment(
            val partId: Long,
            val contentType: String,
            val name: String?,
            val size: Long?,
            val data: String?,
        )

        override fun equals(other: Any?): Boolean {
            return other is MMS && other.messageId == this.messageId
        }

        override fun hashCode(): Int {
            return messageId.hashCode()
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
