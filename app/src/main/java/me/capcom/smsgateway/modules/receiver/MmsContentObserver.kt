package me.capcom.smsgateway.modules.receiver

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MmsContentObserver : KoinComponent {
    private val context: Context by inject()
    private val storage: StateStorage by inject()
    private val receiverSvc: ReceiverService by inject()
    private val logsService: LogsService by inject()

    private var handlerThread: HandlerThread? = null
    private var observer: ContentObserver? = null

    fun start() {
        if (observer != null) {
            return
        }

        if (!canReadSms()) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "MMS inbox observer not started because READ_SMS is not granted",
            )
            return
        }

        // Initialize high-water mark to current max ID if not set
        if (storage.mmsLastProcessedID == 0L) {
            storage.mmsLastProcessedID = queryMaxMmsId()
        }

        val thread = HandlerThread("MmsContentObserver").apply { start() }
        handlerThread = thread

        val obs = object : ContentObserver(Handler(thread.looper)) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                processNewMessages()
            }
        }
        observer = obs

        context.contentResolver.registerContentObserver(
            Uri.parse("content://mms"),
            true,
            obs
        )
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        handlerThread?.quitSafely()
        handlerThread = null
    }

    private fun queryMaxMmsId(): Long {
        if (!canReadSms()) return 0

        val cursor = try {
            context.contentResolver.query(
                Uri.parse("content://mms"),
                arrayOf("_id"),
                null, null,
                "_id DESC LIMIT 1"
            )
        } catch (e: SecurityException) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Unable to initialize MMS inbox high-water mark because provider access was denied",
                mapOf("error" to (e.message ?: e.toString())),
            )
            return 0
        } ?: return 0

        return cursor.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0
        }
    }

    private fun processNewMessages() {
        if (!canReadSms()) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Skipping MMS inbox processing because READ_SMS is not granted",
            )
            return
        }

        val mark = storage.mmsLastProcessedID
        // msg_type 132 = retrieve-conf (fully downloaded), msg_box 1 = inbox
        val cursor = try {
            context.contentResolver.query(
                Uri.parse("content://mms"),
                arrayOf("_id"),
                "_id > ? AND m_type = 132 AND msg_box = 1",
                arrayOf(mark.toString()),
                "_id ASC"
            )
        } catch (e: SecurityException) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Skipping MMS inbox processing because provider access was denied",
                mapOf("error" to (e.message ?: e.toString())),
            )
            return
        } ?: return

        cursor.use { c ->
            while (c.moveToNext()) {
                val mmsId = c.getLong(0)
                try {
                    processMmsDownloaded(mmsId)
                    storage.mmsLastProcessedID = mmsId
                } catch (e: Exception) {
                    logsService.insert(
                        LogEntry.Priority.ERROR,
                        MODULE_NAME,
                        "Failed processing downloaded MMS (id=$mmsId)",
                        mapOf("mmsId" to mmsId)
                    )
                }
            }
        }
    }

    private fun processMmsDownloaded(mmsId: Long) {
        val message = MmsContentReader.read(context, mmsId) ?: return
        receiverSvc.process(
            context,
            InboxMessage.MMS(
                mmsId.toString(),
                message.body,
                message.subject,
                message.attachments.map {
                    InboxMessage.MMS.Attachment(
                        it.partId,
                        it.contentType,
                        it.name,
                        it.size,
                        it.data
                    )
                },
                message.sender,
                message.date,
                message.subscriptionId
            ),
            true,
        )
    }

    private fun canReadSms(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "MmsContentObserver"
    }
}
