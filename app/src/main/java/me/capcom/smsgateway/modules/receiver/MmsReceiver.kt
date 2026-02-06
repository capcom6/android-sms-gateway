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
import me.capcom.smsgateway.modules.receiver.parsers.MmsAttachmentExtractor
import me.capcom.smsgateway.modules.receiver.parsers.MMSParser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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
                    "pdu" to pdu.joinToString("") { "%02x".format(it) },
                )
            )


            val mmsNotification = MMSParser.parseMNotificationInd(pdu)
            val attachments = MmsAttachmentExtractor.extract(
                context,
                mmsNotification.messageId,
                mmsNotification.transactionId,
            )

            Log.d(TAG, "MMS received from ${mmsNotification.from}")

            val mmsMessage = InboxMessage.Mms(
                messageId = mmsNotification.messageId,
                transactionId = mmsNotification.transactionId,
                subject = mmsNotification.subject,
                size = mmsNotification.messageSize,
                contentClass = mmsNotification.contentClass?.name,
                attachments = attachments,
                address = mmsNotification.from.substringBefore('/'),
                date = mmsNotification.date ?: Date(),
                subscriptionId = SubscriptionsHelper.extractSubscriptionId(context, intent),
            )

            // Process the message using the existing ReceiverService
            receiverSvc.process(context, mmsMessage)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing MMS", e)
        }
    }

    companion object {
        private const val TAG = "MmsReceiver"

        private val INSTANCE: MmsReceiver by lazy { MmsReceiver() }

        fun register(context: Context) {
            val filter = IntentFilter().apply {
                addAction(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION)
                addDataType("application/vnd.wap.mms-message")
            }
            context.registerReceiver(
                INSTANCE,
                filter
            )
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
