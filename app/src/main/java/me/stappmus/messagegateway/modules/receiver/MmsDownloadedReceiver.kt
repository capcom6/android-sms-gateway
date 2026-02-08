package me.stappmus.messagegateway.modules.receiver

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.klinker.android.send_message.MmsReceivedReceiver
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.media.MediaService
import me.stappmus.messagegateway.modules.receiver.data.InboxMessage
import me.stappmus.messagegateway.modules.receiver.parsers.MmsAttachmentExtractor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

class MmsDownloadedReceiver : MmsReceivedReceiver(), KoinComponent {
    private val receiverSvc: ReceiverService by inject()
    private val logsService: LogsService by inject()
    private val mediaService: MediaService by inject()

    override fun onMessageReceived(context: Context, uri: Uri) {
        try {
            val row = queryMmsRow(context, uri)
            if (row == null) {
                onError(context, "Could not resolve MMS row for uri=$uri")
                return
            }

            val extraction = MmsAttachmentExtractor.extractFromRow(
                context = context,
                rowId = row.id,
                messageId = row.messageId,
                transactionId = row.transactionId,
                receivedAtEpochMs = row.date.time,
                senderAddress = row.address,
            )
            val attachments = mediaService.cacheIncomingAttachments(
                context,
                extraction.attachments,
            )

            val mmsMessage = InboxMessage.Mms(
                messageId = extraction.providerMessageId ?: row.messageId,
                transactionId = row.transactionId,
                subject = extraction.subject ?: row.subject,
                size = extraction.size ?: row.size,
                contentClass = extraction.contentClass ?: row.contentClass,
                attachments = attachments,
                address = row.address,
                date = row.date,
                subscriptionId = row.subscriptionId,
            )

            receiverSvc.process(context, mmsMessage)

            logsService.insert(
                priority = LogEntry.Priority.DEBUG,
                module = MODULE_NAME,
                message = "MMS downloaded and processed",
                context = mapOf(
                    "uri" to uri.toString(),
                    "transactionId" to row.transactionId,
                    "attachments" to attachments.size,
                )
            )
        } catch (e: Exception) {
            onError(context, "Failed processing downloaded MMS: ${e.message}")
        }
    }

    override fun onError(context: Context, error: String) {
        logsService.insert(
            priority = LogEntry.Priority.WARN,
            module = MODULE_NAME,
            message = "MMS download receiver error",
            context = mapOf("error" to error),
        )
        Log.w(TAG, error)
    }

    private fun queryMmsRow(context: Context, uri: Uri): MmsRow? {
        val projection = arrayOf(
            Telephony.Mms._ID,
            "tr_id",
            "m_id",
            "sub",
            "m_size",
            "ct_cls",
            "date",
            "sub_id",
        )

        return context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            val rowId = cursor.getLongSafe(Telephony.Mms._ID) ?: return null
            val transactionId = cursor.getStringSafe("tr_id")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val messageId = cursor.getStringSafe("m_id")
            val subject = cursor.getStringSafe("sub")
            val contentClass = cursor.getStringSafe("ct_cls")
            val size = cursor.getLongSafe("m_size") ?: 0L
            val dateSeconds = cursor.getLongSafe("date") ?: (System.currentTimeMillis() / 1000L)
            val subscriptionId = cursor.getIntSafe("sub_id")

            val address = queryAddress(context, rowId) ?: "unknown"

            MmsRow(
                id = rowId,
                transactionId = transactionId,
                messageId = messageId,
                subject = subject,
                contentClass = contentClass,
                size = size,
                date = Date(dateSeconds * 1000L),
                address = address,
                subscriptionId = subscriptionId,
            )
        }
    }

    private fun queryAddress(context: Context, rowId: Long): String? {
        val addrUri = Uri.parse("content://mms/$rowId/addr")
        return context.contentResolver.query(
            addrUri,
            arrayOf("address", "type"),
            "type = ?",
            arrayOf("137"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getStringSafe("address")?.substringBefore('/')
            } else {
                null
            }
        }
    }

    private fun Cursor.getStringSafe(columnName: String): String? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) {
            return null
        }
        return getString(index)
    }

    private fun Cursor.getLongSafe(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) {
            return null
        }
        return getLong(index)
    }

    private fun Cursor.getIntSafe(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) {
            return null
        }
        return getInt(index)
    }

    private data class MmsRow(
        val id: Long,
        val transactionId: String,
        val messageId: String?,
        val subject: String?,
        val contentClass: String?,
        val size: Long,
        val date: Date,
        val address: String,
        val subscriptionId: Int?,
    )

    companion object {
        private const val TAG = "MmsDownloadedReceiver"
        private const val MODULE_NAME = "receiver"
    }
}
