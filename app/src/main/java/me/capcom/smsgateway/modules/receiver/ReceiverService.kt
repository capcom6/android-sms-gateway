package me.capcom.smsgateway.modules.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Base64
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.receiver.parsers.MMSRetrieveParser
import me.capcom.smsgateway.receivers.EventsReceiver as GlobalEventsReceiver
import java.io.File
import java.nio.charset.Charset
import me.capcom.smsgateway.modules.incoming.IncomingMessagesService
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.mms.MmsAttachmentStorage
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
    private val attachmentStorage: MmsAttachmentStorage by inject()

    private val eventsReceiver by lazy { EventsReceiver() }
    private val mmsContentObserver by lazy { MmsContentObserver() }
    private val smsContentObserver by lazy { SmsContentObserver() }

    fun start(context: Context) {
        MessagesReceiver.register(context)
        MmsReceiver.register(context)
        eventsReceiver.start()
        mmsContentObserver.start()
        smsContentObserver.start()
    }

    fun stop(context: Context) {
        smsContentObserver.stop()
        mmsContentObserver.stop()
        eventsReceiver.stop()
        MmsReceiver.unregister(context)
        MessagesReceiver.unregister(context)
    }

    suspend fun export(context: Context, period: Pair<Date, Date>, triggerWebhooks: Boolean) {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::export - start",
            mapOf("period" to period)
        )

        select(context, period)
            .forEach {
                process(context, it, triggerWebhooks)
            }

        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::export - end",
            mapOf("period" to period)
        )
    }

    /**
     * Called from EventsReceiver when `SmsManager.downloadMultimediaMessage`
     * finishes. Parses the downloaded M-Retrieve.conf PDU and pushes it into
     * the same inbox/webhook pipeline as system-downloaded MMS.
     */
    fun processDownloadedMmsIntent(context: Context, intent: Intent, resultCode: Int) {
        val messageId = intent.getStringExtra(GlobalEventsReceiver.EXTRA_MESSAGE_ID) ?: return
        val path = intent.getStringExtra(GlobalEventsReceiver.EXTRA_PDU_PATH)

        if (resultCode != Activity.RESULT_OK) {
            logsService.insert(
                LogEntry.Priority.ERROR,
                MODULE_NAME,
                "MMS download failed",
                mapOf("messageId" to messageId, "resultCode" to resultCode)
            )
            path?.let { File(it).delete() }
            return
        }
        if (path == null) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "MMS download completed without PDU path",
                mapOf("messageId" to messageId)
            )
            return
        }

        val file = File(path)
        if (!file.exists()) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "MMS PDU file not found",
                mapOf("messageId" to messageId, "path" to path)
            )
            return
        }

        val retrieved = try {
            MMSRetrieveParser.parse(file.readBytes())
        } catch (e: Exception) {
            logsService.insert(
                LogEntry.Priority.ERROR,
                MODULE_NAME,
                "Failed to parse M-Retrieve.conf",
                mapOf(
                    "messageId" to messageId,
                    "error" to (e.message ?: e.toString()),
                )
            )
            return
        } finally {
            file.delete()
        }

        val attachments = mutableListOf<InboxMessage.MMS.Attachment>()
        val bodyParts = mutableListOf<String>()
        retrieved.parts.forEach { part ->
            val mt = part.contentType.substringBefore(';').trim().lowercase()
            val isSmil = mt == "application/smil"
            if (isSmil) return@forEach

            if (mt == "text/plain") {
                val charset = try {
                    Charset.forName(part.charset ?: "UTF-8")
                } catch (_: Exception) {
                    Charsets.UTF_8
                }
                bodyParts += String(part.data, charset)
            }

            val name = part.name ?: part.contentLocation
            val base64 = Base64.encodeToString(part.data, Base64.NO_WRAP)
            try {
                attachmentStorage.store(messageId, part.partId, name, part.data)
            } catch (_: Exception) {
            }
            attachments += InboxMessage.MMS.Attachment(
                partId = part.partId,
                contentType = part.contentType,
                name = name,
                size = part.data.size.toLong(),
                data = base64,
            )
        }

        val sender = retrieved.from ?: "unknown"
        val mmsInbox = InboxMessage.MMS(
            messageId = messageId,
            body = bodyParts.joinToString("\n").takeIf { it.isNotEmpty() },
            subject = retrieved.subject,
            attachments = attachments,
            address = sender,
            date = retrieved.date ?: Date(),
            subscriptionId = null,
        )
        process(context, mmsInbox, true)
    }

    fun process(context: Context, message: InboxMessage, triggerWebhooks: Boolean) {
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

        if (triggerWebhooks) {
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

                is InboxMessage.MMS -> {
                    // Persist each attachment to our own private storage so
                    // consumers can fetch by URL even after the system MMS
                    // provider has been cleaned up.
                    message.attachments.forEach { att ->
                        val decoded = att.data?.let { runCatching {
                            android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                        }.getOrNull() }
                        if (decoded != null) {
                            try {
                                attachmentStorage.store(
                                    message.messageId,
                                    att.partId,
                                    att.name,
                                    decoded,
                                )
                            } catch (e: Exception) {
                                logsService.insert(
                                    LogEntry.Priority.WARN,
                                    MODULE_NAME,
                                    "Failed to persist MMS attachment",
                                    mapOf(
                                        "messageId" to message.messageId,
                                        "partId" to att.partId,
                                        "error" to (e.message ?: e.toString()),
                                    )
                                )
                            }
                        }
                    }

                    WebHookEvent.MmsDownloaded to MmsDownloadedPayload(
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
                                data = it.data,
                                url = "/inbox/${message.messageId}/attachments/${it.partId}",
                            )
                        },
                        receivedAt = message.date,
                    )
                }
            }

            webHooksService.emit(context, type, payload)
        }

        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::process - message processed",
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