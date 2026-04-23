package me.capcom.smsgateway.modules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import me.capcom.smsgateway.helpers.DefaultSmsAppHelper
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.mms.MmsDownloader
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import me.capcom.smsgateway.modules.receiver.parsers.MMSParser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Collections
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class MmsReceiver : BroadcastReceiver(), KoinComponent {
    private val receiverSvc: ReceiverService by inject()
    private val logsService: LogsService by inject()
    private val mmsDownloader: MmsDownloader by inject()

    // Transaction IDs we've already kicked off a download for this process.
    // The same WAP push reliably fires both WAP_PUSH_RECEIVED (to observers)
    // and WAP_PUSH_DELIVER (to the default SMS app) on most carriers. We now
    // drive the download from either, so we need a dedupe.
    private val triggeredTxns: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION
            && action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION
        ) {
            return
        }

        if (intent.type != "application/vnd.wap.mms-message") {
            return
        }

        try {
            val bundle: Bundle = intent.extras ?: run {
                Log.w(TAG, "No extras found in MMS intent")
                return
            }

            val pdu = bundle.getByteArray("data") ?: bundle.getByteArray("pdu")
            if (pdu == null) {
                Log.w(TAG, "No PDU data found in MMS intent")
                return
            }

            logsService.insert(
                LogEntry.Priority.DEBUG,
                MODULE_NAME,
                "MMS WAP push received",
                mapOf(
                    "action" to action,
                    "extras" to bundle.keySet().joinToString(", ") { it },
                    "pdu" to pdu.joinToString("") { "%02x".format(it) },
                )
            )

            val notification = MMSParser.parseMNotificationInd(pdu)
            val messageId = notification.messageId ?: notification.transactionId
            val subscriptionId = SubscriptionsHelper.extractSubscriptionId(context, intent)

            val headers = InboxMessage.MmsHeaders(
                messageId = notification.messageId,
                transactionId = notification.transactionId,
                subject = notification.subject,
                size = notification.messageSize,
                contentClass = notification.contentClass?.name,
                address = notification.from.substringBefore('/'),
                date = notification.date ?: Date(),
                subscriptionId = subscriptionId,
            )
            receiverSvc.process(context, headers, true)

            // When we are the default SMS app the download does not happen
            // automatically — trigger it ourselves so MmsContentObserver can
            // pick the message up once the PDU is written into the system
            // provider.
            //
            // Historically we only ran this on WAP_PUSH_DELIVER, but on
            // Pixel+Verizon the vendor CarrierMessagingService swallows the
            // DELIVER broadcast (same pattern as SMS_DELIVER) and only
            // WAP_PUSH_RECEIVED reaches us. Fire the download from either,
            // deduped by transactionId so the per-carrier double-broadcast
            // doesn't queue twice.
            val isDefault = DefaultSmsAppHelper.isDefault(context)
            if (isDefault && triggeredTxns.add(notification.transactionId)) {
                val location = notification.contentLocation
                if (!location.isNullOrBlank()) {
                    try {
                        mmsDownloader.download(
                            messageId = messageId,
                            transactionId = notification.transactionId,
                            contentLocation = location,
                            subscriptionId = subscriptionId,
                        )
                    } catch (e: Throwable) {
                        logsService.insert(
                            LogEntry.Priority.ERROR,
                            MODULE_NAME,
                            "Failed to trigger MMS download",
                            mapOf(
                                "messageId" to messageId,
                                "contentLocation" to location,
                                "error" to e.message,
                            )
                        )
                    }
                } else {
                    logsService.insert(
                        LogEntry.Priority.WARN,
                        MODULE_NAME,
                        "MMS notification missing Content-Location; cannot download",
                        mapOf("transactionId" to notification.transactionId)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing MMS", e)
            logsService.insert(
                LogEntry.Priority.ERROR,
                MODULE_NAME,
                "MMS processing failure",
                mapOf("error" to (e.message ?: e.toString()))
            )
        }
    }

    companion object {
        private const val TAG = "MmsReceiver"

        private val INSTANCE: MmsReceiver by lazy { MmsReceiver() }

        /**
         * Registered dynamically so we receive WAP_PUSH_RECEIVED even when not
         * the default SMS app. WAP_PUSH_DELIVER (sent only to the default app)
         * is declared statically in the manifest.
         */
        fun register(context: Context) {
            val filter = IntentFilter().apply {
                addAction(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION)
                addDataType("application/vnd.wap.mms-message")
            }
            context.registerReceiver(INSTANCE, filter)
        }

        fun unregister(context: Context) {
            try {
                context.unregisterReceiver(INSTANCE)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered", e)
            }
        }
    }
}
