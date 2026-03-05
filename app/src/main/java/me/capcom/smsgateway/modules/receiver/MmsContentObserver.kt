package me.capcom.smsgateway.modules.receiver

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread

class MmsContentObserver(
    private val context: Context,
    private val onMmsDownloaded: (mmsId: Long) -> Unit,
) {
    private val prefs = context.getSharedPreferences("mms_observer", Context.MODE_PRIVATE)
    private var handlerThread: HandlerThread? = null
    private var observer: Observer? = null

    private var highWaterMark: Long
        get() = prefs.getLong(KEY_HIGH_WATER_MARK, 0)
        set(value) = prefs.edit().putLong(KEY_HIGH_WATER_MARK, value).apply()

    fun start() {
        if (observer != null) {
            return
        }

        // Initialize high-water mark to current max ID if not set
        if (highWaterMark == 0L) {
            highWaterMark = queryMaxMmsId()
        }

        val thread = HandlerThread("MmsContentObserver").apply { start() }
        handlerThread = thread

        val obs = Observer(Handler(thread.looper))
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
            "_id DESC"
        ) ?: return 0

        return cursor.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0
        }
    }

    private fun processNewMessages() {
        val mark = highWaterMark
        // msg_type 132 = retrieve-conf (fully downloaded), msg_box 1 = inbox
        val cursor = context.contentResolver.query(
            Uri.parse("content://mms"),
            arrayOf("_id"),
            "_id > ? AND m_type = 132 AND msg_box = 1",
            arrayOf(mark.toString()),
            "_id ASC"
        ) ?: return

        var newMark = mark
        cursor.use { c ->
            while (c.moveToNext()) {
                val mmsId = c.getLong(0)
                onMmsDownloaded(mmsId)
                if (mmsId > newMark) {
                    newMark = mmsId
                }
            }
        }

        if (newMark > mark) {
            highWaterMark = newMark
        }
    }

    private inner class Observer(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            processNewMessages()
        }
    }

    companion object {
        private const val KEY_HIGH_WATER_MARK = "last_processed_mms_id"
    }
}
