package me.capcom.smsgateway.modules.receiver

import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.util.Base64
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.incoming.IncomingMessagesService
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import me.capcom.smsgateway.modules.webhooks.WebHooksService
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import me.capcom.smsgateway.modules.webhooks.payload.MmsDownloadedPayload
import me.capcom.smsgateway.modules.webhooks.payload.MmsReceivedPayload
import me.capcom.smsgateway.modules.webhooks.payload.SmsEventPayload
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

class ReceiverService : KoinComponent {
    private val webHooksService: WebHooksService by inject()
    private val logsService: LogsService by inject()
    private val incomingMessagesService: IncomingMessagesService by inject()

    private val eventsReceiver by lazy { EventsReceiver() }
    private val mmsContentObserver by lazy { MmsContentObserver() }

    fun start(context: Context) {
        MessagesReceiver.register(context)
        MmsReceiver.register(context)
        eventsReceiver.start()
        mmsContentObserver.start()
    }

    fun stop(context: Context) {
        mmsContentObserver.stop()
        eventsReceiver.stop()
        MmsReceiver.unregister(context)
        MessagesReceiver.unregister(context)
    }

    fun export(context: Context, period: Pair<Date, Date>) {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::export - start",
            mapOf("period" to period)
        )

        select(context, period)
            .forEach {
                process(context, it)
            }

        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::export - end",
            mapOf("period" to period)
        )
    }

    fun process(context: Context, message: InboxMessage) {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::process - message received",
            mapOf("message" to message)
        )

        val simSlotIndex = message.subscriptionId?.let {
            SubscriptionsHelper.getSimSlotIndex(context, it)
        }
        val simNumber = simSlotIndex?.let { it + 1 }
        val recipient = simSlotIndex?.let {
            SubscriptionsHelper.getPhoneNumber(context, it)
        }

        val sender = message.address

        try {
            incomingMessagesService.save(message, sender, recipient, simNumber)
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


        val (type, payload) = when (message) {
            is InboxMessage.Text -> WebHookEvent.SmsReceived to SmsEventPayload.SmsReceived(
                messageId = message.hashCode().toUInt().toString(16),
                message = message.text,
                sender = sender,
                simNumber = simNumber,
                receivedAt = message.date,
                recipient = recipient,
            )

            is InboxMessage.Data -> WebHookEvent.SmsDataReceived to SmsEventPayload.SmsDataReceived(
                messageId = message.hashCode().toUInt().toString(16),
                data = Base64.encodeToString(message.data, Base64.NO_WRAP),
                simNumber = simNumber,
                receivedAt = message.date,
                sender = sender,
                recipient = recipient,
            )

            is InboxMessage.MmsHeaders -> WebHookEvent.MmsReceived to MmsReceivedPayload(
                messageId = message.messageId ?: message.transactionId,
                simNumber = simNumber,
                transactionId = message.transactionId,
                subject = message.subject,
                size = message.size,
                contentClass = message.contentClass,
                receivedAt = message.date,
                sender = sender,
                recipient = recipient,
            )

            is InboxMessage.MMS -> WebHookEvent.MmsDownloaded to MmsDownloadedPayload(
                messageId = message.messageId,
                sender = sender,
                recipient = recipient,
                simNumber = simNumber,
                body = message.body,
                subject = message.subject,
                attachments = message.attachments.map {
                    MmsDownloadedPayload.Attachment(
                        partId = it.partId,
                        contentType = it.contentType,
                        name = it.name,
                        size = it.size,
                        data = it.data
                    )
                },
                receivedAt = message.date,
            )
        }

        webHooksService.emit(context, type, payload)

        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::process - message processed",
            mapOf("type" to type, "payload" to payload)
        )
    }


    fun select(context: Context, period: Pair<Date, Date>): List<InboxMessage> {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::select - start",
            mapOf("period" to period)
        )

        val projection = mutableListOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            projection += Telephony.Sms.SUBSCRIPTION_ID
        }

        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"
        val selectionArgs = arrayOf(
            period.first.time.toString(),
            period.second.time.toString()
        )
        val sortOrder = Telephony.Sms.DATE

        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection.toTypedArray(),
            selection,
            selectionArgs,
            sortOrder
        )

        val messages = mutableListOf<InboxMessage>()

        cursor?.use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(
                    InboxMessage.Text(
                        address = cursor.getString(1),
                        date = Date(cursor.getLong(2)),
                        text = cursor.getString(3),
                        subscriptionId = when {
                            projection.size > 4 -> cursor.getInt(4).takeIf { it >= 0 }
                            else -> null
                        }
                    )
                )
            }
        }

        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::select - end",
            mapOf("messages" to messages.size)
        )

        return messages
    }
}