package me.capcom.smsgateway.modules.receiver

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.Telephony
import androidx.core.content.ContextCompat
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

/**
 * Fallback SMS ingest for carriers / device configurations where the
 * `SMS_DELIVER` broadcast is intercepted upstream (e.g. Verizon on Pixel
 * routes inbound SMS through a vendor CarrierMessagingService and
 * `DefaultSmsReceiver.onReceive` is never invoked — but the row still lands
 * in `content://sms/inbox`).
 *
 * Watches the inbox content provider, picks up rows with `_id` above a
 * high-water mark, and feeds them through the same `ReceiverService.process`
 * pipeline that `DefaultSmsReceiver` would normally drive. Mirrors
 * `MmsContentObserver`.
 */
class SmsContentObserver : KoinComponent {
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
                "SMS inbox observer not started because READ_SMS is not granted",
            )
            return
        }

        // Initialize high-water mark to current max ID so existing rows in
        // the inbox are not re-processed on first start.
        if (storage.smsLastProcessedID == 0L) {
            storage.smsLastProcessedID = queryMaxSmsId()
        }

        val thread = HandlerThread("SmsContentObserver").apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)

        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                processNewMessages()
            }
        }
        observer = obs

        // Observe the parent sms:// URI with notifyForDescendants=true so
        // we catch inserts into the inbox regardless of which internal URI
        // the system provider notifies under.
        context.contentResolver.registerContentObserver(
            Uri.parse("content://sms"),
            true,
            obs,
        )

        // Catch up rows that arrived while the app process was stopped or before
        // READ_SMS was granted. ContentObserver callbacks are edge-triggered, so
        // already-inserted SMS rows would otherwise remain pending forever.
        handler.post { processNewMessages() }
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        handlerThread?.quitSafely()
        handlerThread = null
    }

    private fun queryMaxSmsId(): Long {
        if (!canReadSms()) return 0

        val cursor = try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null, null,
                Telephony.Sms._ID + " DESC LIMIT 1",
            )
        } catch (e: SecurityException) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Unable to initialize SMS inbox high-water mark because provider access was denied",
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
                "Skipping SMS inbox processing because READ_SMS is not granted",
            )
            return
        }

        val mark = storage.smsLastProcessedID

        val projection = mutableListOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            projection += Telephony.Sms.SUBSCRIPTION_ID
        }

        val cursor = try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection.toTypedArray(),
                Telephony.Sms._ID + " > ?",
                arrayOf(mark.toString()),
                Telephony.Sms._ID + " ASC",
            )
        } catch (e: SecurityException) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Skipping SMS inbox processing because provider access was denied",
                mapOf("error" to (e.message ?: e.toString())),
            )
            return
        } ?: return

        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val address = c.getString(1) ?: ""
                val date = Date(c.getLong(2))
                val body = c.getString(3) ?: ""
                val subId = if (projection.size > 4) {
                    c.getInt(4).takeIf { it >= 0 }
                } else {
                    null
                }

                try {
                    receiverSvc.process(
                        context,
                        InboxMessage.Text(body, address, date, subId),
                        true,
                    )
                } catch (e: Exception) {
                    logsService.insert(
                        LogEntry.Priority.ERROR,
                        MODULE_NAME,
                        "Failed processing inbox SMS (id=$id)",
                        mapOf("smsId" to id, "error" to (e.message ?: e.toString())),
                    )
                }
                storage.smsLastProcessedID = id
            }
        }
    }

    private fun canReadSms(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "SmsContentObserver"
    }
}
