package me.capcom.smsgateway.modules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import me.capcom.smsgateway.modules.receiver.parsers.MMSParser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest
import java.util.Date

class MmsReceiver : BroadcastReceiver(), KoinComponent {
    private val receiverSvc: ReceiverService by inject()
    private val logsService: LogsService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION) {
            return
        }

        val contentType = intent.type
        if (contentType != "application/vnd.wap.mms-message") {
            return
        }


        try {
            val bundle: Bundle? = intent.extras
            if (bundle == null) {
                Log.w(TAG, "No extras found in MMS intent")
                return
            }

            // Extract MMS metadata from the intent
            val pdu = bundle.getByteArray("data") ?: bundle.getByteArray("pdu")

            if (pdu == null) {
                Log.w(TAG, "No PDU data found in MMS intent")
                return
            }

            logsService.insert(
                LogEntry.Priority.DEBUG,
                MODULE_NAME,
                "MMS received",
                mapOf(
                    "data" to intent.dataString,
                    "extras" to bundle.keySet().joinToString(", ") { it },
                    "uri" to intent.extras?.getString("uri"),
                    "header" to intent.extras?.getByteArray("header")
                        ?.joinToString("") { "%02x".format(it) },
                    "pduSizeBytes" to pdu.size,
                    "pduSha256" to MessageDigest.getInstance("SHA-256")
                        .digest(pdu)
                        .joinToString("") { "%02x".format(it) },
                )
            )


            val mmsNotification = MMSParser.parseMNotificationInd(pdu)

            Log.d(TAG, "MMS received from ${mmsNotification.from}")

            val mmsMessage = InboxMessage.MmsHeaders(
                messageId = mmsNotification.messageId,
                transactionId = mmsNotification.transactionId,
                subject = mmsNotification.subject,
                size = mmsNotification.messageSize,
                contentClass = mmsNotification.contentClass?.name,
                address = mmsNotification.from.substringBefore('/'),
                date = mmsNotification.date ?: Date(),
                subscriptionId = SubscriptionsHelper.extractSubscriptionId(context, intent),
            )

            // Process the message using the existing ReceiverService
            receiverSvc.process(context, mmsMessage, true)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing MMS", e)
        }
    }

    companion object {
        private const val TAG = "MmsReceiver"

        private val INSTANCE: MmsReceiver by lazy { MmsReceiver() }

        fun register(context: Context) {
            val appContext = context.applicationContext
            unregister(appContext)

            val filter = IntentFilter().apply {
                addAction(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION)
                addDataType("application/vnd.wap.mms-message")
            }
            appContext.registerReceiver(
                INSTANCE,
                filter
            )
        }

        fun unregister(context: Context) {
            try {
                context.applicationContext.unregisterReceiver(INSTANCE)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered", e)
            }
        }
    }
}