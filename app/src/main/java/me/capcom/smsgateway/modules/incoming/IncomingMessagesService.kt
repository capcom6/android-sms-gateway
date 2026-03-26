package me.capcom.smsgateway.modules.incoming

import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import java.util.UUID

class IncomingMessagesService(
    private val repository: IncomingMessagesRepository,
) {
    fun save(message: InboxMessage, sender: String, recipient: String?, simNumber: Int?) {
        val type = when (message) {
            is InboxMessage.Text -> IncomingMessageType.SMS
            is InboxMessage.Data -> IncomingMessageType.DATA_SMS
            is InboxMessage.MmsHeaders -> IncomingMessageType.MMS
            is InboxMessage.MMS -> IncomingMessageType.MMS_DOWNLOADED
        }

        repository.insert(
            IncomingMessage(
                id = buildId(message),
                type = type,
                sender = sender,
                recipient = recipient,
                simNumber = simNumber,
                subscriptionId = message.subscriptionId,
                contentPreview = message.toPreview(),
                createdAt = message.date.time,
            )
        )
    }

    private fun buildId(message: InboxMessage): String {
        val base = when (message) {
            is InboxMessage.MmsHeaders -> message.messageId ?: message.transactionId
            is InboxMessage.MMS -> message.messageId
            else -> null
        }

        return base ?: UUID.nameUUIDFromBytes(
            "${message.address}-${message.date.time}-${message.subscriptionId}".toByteArray()
        ).toString()
    }

    private fun InboxMessage.toPreview(): String {
        return when (this) {
            is InboxMessage.Text -> text
            is InboxMessage.Data -> data?.let { "Binary data (${it.size} bytes)" } ?: "Binary data"
            is InboxMessage.MmsHeaders -> subject ?: "MMS notification"
            is InboxMessage.MMS -> body ?: subject ?: "MMS content"
        }
    }
}
