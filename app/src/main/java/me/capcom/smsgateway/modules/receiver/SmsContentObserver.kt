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
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.messages.MessagesRepository
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import me.capcom.smsgateway.modules.webhooks.WebHooksService
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import me.capcom.smsgateway.modules.webhooks.payload.SmsEventPayload
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

/**
 * Fallback SMS ingest for carriers / device configurations where the
 * `SMS_RECEIVED` broadcast is intercepted upstream (e.g. Verizon on Pixel
 * routes inbound SMS through a vendor CarrierMessagingService and the
 * broadcast is never delivered — but the row still lands in
 * `content://sms/inbox`).
 *
 * Watches the inbox content provider, picks up rows with `_id` above a
 * high-water mark, and feeds them through `ReceiverService.process`.
 * Mirrors `MmsContentObserver`.
 */
class SmsContentObserver : KoinComponent {
    private val context: Context by inject()
    private val storage: StateStorage by inject()
    private val receiverSvc: ReceiverService by inject()
    private val logsService: LogsService by inject()
    private val receiverSettings: ReceiverSettings by inject()
    private val webHooksService: WebHooksService by inject()
    private val messagesRepository: MessagesRepository by inject()

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
            storage.smsLastProcessedID = queryMaxSmsId(Telephony.Sms.Inbox.CONTENT_URI)
        }
        if (receiverSettings.deviceSentEnabled && storage.sentSmsLastProcessedID == 0L) {
            storage.sentSmsLastProcessedID = queryMaxSmsId(Telephony.Sms.Sent.CONTENT_URI)
        }

        val thread = HandlerThread("SmsContentObserver").apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)

        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                processNewMessages()
                processNewSentMessages()
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
        handler.post {
            processNewMessages()
            processNewSentMessages()
        }
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        handlerThread?.quitSafely()
        handlerThread = null
    }

    private fun queryMaxSmsId(uri: Uri): Long {
        if (!canReadSms()) return 0

        val cursor = try {
            context.contentResolver.query(
                uri,
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
            Telephony.Sms.DATE_SENT,
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

    /**
     * Picks up outgoing SMS written to `content://sms/sent` by the device's own
     * messaging app and reports them through the `sms:device-sent` webhook.
     *
     * Without this, a conversation seen through the gateway is one-sided: replies
     * typed directly on the phone are invisible to the integration, because the
     * gateway only knows about messages it sent itself through its API.
     *
     * Uses the same high-water mark technique as the inbox scan, with a separate
     * marker, and is skipped entirely unless explicitly enabled in settings.
     */
    private fun processNewSentMessages() {
        if (!receiverSettings.deviceSentEnabled) return

        if (!canReadSms()) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Skipping sent SMS processing because READ_SMS is not granted",
            )
            return
        }

        val mark = storage.sentSmsLastProcessedID

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
                Telephony.Sms.Sent.CONTENT_URI,
                projection.toTypedArray(),
                Telephony.Sms._ID + " > ?",
                arrayOf(mark.toString()),
                Telephony.Sms._ID + " ASC",
            )
        } catch (e: SecurityException) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Skipping sent SMS processing because provider access was denied",
                mapOf("error" to (e.message ?: e.toString())),
            )
            return
        } ?: return

        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val address = c.getString(1) ?: ""
                // DATE_SENT is commonly 0 for locally composed messages, so the
                // provider's DATE (row insertion time) is the reliable timestamp here.
                val date = Date(c.getLong(2))
                val body = c.getString(3) ?: ""
                val subId = if (projection.size > 4) {
                    c.getInt(4).takeIf { it >= 0 }
                } else {
                    null
                }

                if (isGatewayEcho(address, body, date)) {
                    storage.sentSmsLastProcessedID = id
                    continue
                }

                try {
                    val simSlotIndex = subId?.let {
                        SubscriptionsHelper.getSimSlotIndex(context, it)
                    }
                    webHooksService.emit(
                        context,
                        WebHookEvent.SmsDeviceSent,
                        SmsEventPayload.SmsDeviceSent(
                            messageId = "sent:$id",
                            sender = simSlotIndex?.let {
                                SubscriptionsHelper.getPhoneNumber(context, it)
                            },
                            recipient = address,
                            simNumber = simSlotIndex?.let { it + 1 },
                            message = body,
                            sentAt = date,
                        ),
                    )
                } catch (e: Exception) {
                    logsService.insert(
                        LogEntry.Priority.ERROR,
                        MODULE_NAME,
                        "Failed processing sent SMS (id=$id)",
                        mapOf("smsId" to id, "error" to (e.message ?: e.toString())),
                    )
                }
                storage.sentSmsLastProcessedID = id
            }
        }
    }

    /**
     * Whether a row in the sent box is really this gateway's own API send, mirrored into
     * the provider by the messaging stack, rather than a message a human composed on the
     * phone. Such a row would otherwise be reported as `sms:device-sent` on top of the
     * `sms:sent` already emitted for it.
     *
     * The gateway itself never writes to the SMS provider (it holds neither `WRITE_SMS`
     * nor the default-SMS-app role) and `SmsManager.sendTextMessage` does not persist on
     * stock Android, so on AOSP this should never match. Some OEM builds do mirror sent
     * messages regardless of the sending app, and this guards that case.
     *
     * Deliberately conservative: on any failure it returns false, so the worst outcome is
     * a duplicate event rather than a silently dropped message.
     */
    private fun isGatewayEcho(address: String, body: String, sentAt: Date): Boolean {
        val digits = address.filter { it.isDigit() }
        if (digits.isEmpty()) return false

        return try {
            messagesRepository.wasSentByGateway(
                phoneSuffix = digits.takeLast(PHONE_MATCH_DIGITS),
                content = body,
                since = sentAt.time - GATEWAY_ECHO_WINDOW_MS,
            )
        } catch (e: Exception) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Could not check whether a sent SMS originated from the gateway",
                mapOf("error" to (e.message ?: e.toString())),
            )
            false
        }
    }

    private fun canReadSms(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "SmsContentObserver"

        /** How far back to look for a matching gateway send. */
        private const val GATEWAY_ECHO_WINDOW_MS = 5 * 60 * 1000L

        /** Trailing digits compared, since the sent box may store a local number format. */
        private const val PHONE_MATCH_DIGITS = 9
    }
}
