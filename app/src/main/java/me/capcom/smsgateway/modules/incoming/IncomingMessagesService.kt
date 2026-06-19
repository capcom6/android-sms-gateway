package me.capcom.smsgateway.modules.incoming

import android.content.Context
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository
import me.capcom.smsgateway.modules.incoming.workers.IncomingLogTruncateWorker
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.receiver.data.InboxMessage

class IncomingMessagesService(
    private val context: Context,
    private val settings: IncomingMessagesSettings,
    private val repository: IncomingMessagesRepository,
    private val logsService: LogsService,
) {
    fun save(message: InboxMessage): IncomingMessage {
        val simSlotIndex = message.subscriptionId?.let {
            SubscriptionsHelper.getSimSlotIndex(context, it)
        }
        val simNumber = simSlotIndex?.let { it + 1 }
        val recipient = simSlotIndex?.let {
            SubscriptionsHelper.getPhoneNumber(context, it)
        }

        val type = when (message) {
            is InboxMessage.Text -> IncomingMessageType.SMS
            is InboxMessage.Data -> IncomingMessageType.DATA_SMS
            is InboxMessage.MmsHeaders -> IncomingMessageType.MMS
            is InboxMessage.MMS -> IncomingMessageType.MMS_DOWNLOADED
        }

        return IncomingMessage(
            id = buildId(message),
            type = type,
            sender = message.address,
            recipient = recipient,
            simNumber = simNumber,
            subscriptionId = message.subscriptionId,
            contentPreview = message.toPreview(),
            createdAt = message.date.time,
        ).also {
            try {
                repository.insert(it)
            } catch (e: Exception) {
                logsService.insert(
                    LogEntry.Priority.ERROR,
                    MODULE_NAME,
                    "Failed to save message",
                    mapOf(
                        "message" to message,
                        "exception" to e.stackTraceToString(),
                    )
                )
            }
        }
    }

    suspend fun count(type: IncomingMessageType?, from: Long, to: Long): Int {
        return repository.count(type, from, to)
    }

    suspend fun select(
        type: IncomingMessageType?,
        from: Long,
        to: Long,
        limit: Int,
        offset: Int
    ): List<IncomingMessage> {
        return repository.select(type, from, to, limit, offset)
    }

    fun getById(id: String): IncomingMessage? {
        return repository.selectById(id)
    }

    fun isMessageProcessed(message: InboxMessage): Boolean {
        return getById(buildId(message)) != null
    }

    fun start(context: Context) {
        IncomingLogTruncateWorker.start(context)
    }

    fun stop(context: Context) {
        IncomingLogTruncateWorker.stop(context)
    }

    suspend fun truncateLog() {
        val lifetime = settings.lifetimeDays ?: return
        repository.truncate(System.currentTimeMillis() - lifetime * 86400000L)
    }

    private fun buildId(message: InboxMessage): String {
        val prefix = when (message) {
            is InboxMessage.Data -> "data:"
            is InboxMessage.MMS -> "mms:"
            is InboxMessage.MmsHeaders -> "mms-header:"
            is InboxMessage.Text -> "text:"
        }

        return prefix + message.hashCode().toString()
    }

    private fun InboxMessage.toPreview(): String {
        return when (this) {
            is InboxMessage.Text -> text
            is InboxMessage.Data -> data?.let { bytes ->
                val preview = bytes.take(64).joinToString(separator = "") { "%02x".format(it) }
                if (bytes.size > 64) "$preview..." else preview
            }
                ?: "Empty data"

            is InboxMessage.MmsHeaders -> subject ?: "MMS notification"
            is InboxMessage.MMS -> body ?: subject ?: "MMS content"
        }
    }
}
