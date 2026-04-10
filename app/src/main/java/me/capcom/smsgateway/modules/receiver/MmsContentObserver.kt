package me.capcom.smsgateway.modules.receiver

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
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
        val cursor = context.contentResolver.query(
            Uri.parse("content://mms"),
            arrayOf("_id"),
            null, null,
            "_id DESC LIMIT 1"
        ) ?: return 0

        return cursor.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0
        }
    }

    private fun processNewMessages() {
        val mark = storage.mmsLastProcessedID
        // msg_type 132 = retrieve-conf (fully downloaded), msg_box 1 = inbox
        val cursor = context.contentResolver.query(
            Uri.parse("content://mms"),
            arrayOf("_id"),
            "_id > ? AND m_type = 132 AND msg_box = 1",
            arrayOf(mark.toString()),
            "_id ASC"
        ) ?: return

        cursor.use { c ->
            while (c.moveToNext()) {
                val mmsId = c.getLong(0)
                try {
                    processMmsDownloaded(mmsId)
                } catch (e: Exception) {
                    logsService.insert(
                        LogEntry.Priority.ERROR,
                        MODULE_NAME,
                        "Failed processing downloaded MMS (id=$mmsId)",
                        mapOf("mmsId" to mmsId)
                    )
                }
                storage.mmsLastProcessedID = mmsId
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

    companion object {
        private const val TAG = "MmsContentObserver"
    }
}
