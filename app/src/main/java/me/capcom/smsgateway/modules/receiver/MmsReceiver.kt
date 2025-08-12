package me.capcom.smsgateway.modules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
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
            // extras contain:
            //		android.telephony.extra.SUBSCRIPTION_INDEX=2,
            //		header=[97, 112, 112, 108, 105, 99, 97, 116, 105, 111, 110, 47, 118, 110, 100, 46, 119, 97, 112, 46, 109, 109, 115, 45, 109, 101, 115, 115, 97, 103, 101, 0, -76, -121, -81, -124],
            //			00000000  61 70 70 6c 69 63 61 74  69 6f 6e 2f 76 6e 64 2e  |application/vnd.|
            //			00000010  77 61 70 2e 6d 6d 73 2d  6d 65 73 73 61 67 65 00  |wap.mms-message.|
            //			00000020  b4 87 af 84
            //		android.telephony.extra.SLOT_INDEX=0,
            //		pduType=6,
            //		data=[-116, -126, -104, 49, 103, 81, 4...],
            //		phone=0,
            //		subscription=2,
            //		transactionId=41
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

            // Extract transaction ID from the PDU header
//            val transactionId = extractTransactionId(pdu)

            // Extract sender address from the intent
//            val sender = extractSenderAddress(context, intent)

            // Extract timestamp
//            val timestamp = extractTimestamp(intent)

            // Extract subscription ID
            val subscriptionId = extractSubscriptionId(context, intent)

            logsService.insert(
                LogEntry.Priority.DEBUG,
                MODULE_NAME,
                "MMS received from $subscriptionId",
                mapOf(
                    "data" to intent.dataString,
                    "extras" to bundle.keySet().joinToString(", ") { it },
                    "uri" to intent.extras?.getString("uri"),
                    "pdu" to pdu.joinToString("") { "%02x".format(it) },
                )
            )

            Log.d(TAG, "MMS received from $subscriptionId")

            // Create a minimal MMS message representation
            // We only store metadata as per requirements, no attachment processing
//            val mmsMessage = InboxMessage.Mms(
//                transactionId = transactionId,
//                subject = null, // Subject would need to be extracted from PDU for real implementation
//                attachmentCount = 0, // Attachment count would need to be extracted from PDU
//                size = 0, // Size would need to be extracted from PDU
//                address = sender,
//                date = timestamp,
//                subscriptionId = subscriptionId
//            )

            // Process the message using the existing ReceiverService
//            receiverSvc.process(context, mmsMessage)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing MMS", e)
        }
    }

    private fun extractTransactionId(pdu: ByteArray): String {
        try {
            // Extract transaction ID from PDU header (first 8 bytes typically)
            // This is a simplified extraction - real implementation would parse PDU properly
            val transactionIdBytes = pdu.copyOfRange(0, minOf(8, pdu.size))
            return transactionIdBytes.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract transaction ID from PDU", e)
            return "unknown"
        }
    }

    private fun extractSenderAddress(context: Context, intent: Intent): String {
        try {
            // Try to get sender from the intent extras first
            val address = intent.getStringExtra("address") 
                ?: intent.getStringExtra("from")
                ?: intent.getStringExtra("sender")
            
            if (address != null) {
                return address
            }

            // Fallback: try to extract from URI if present
            val uri = intent.data
            if (uri != null) {
                val scheme = uri.scheme
                val host = uri.host
                val path = uri.path
                
                if (scheme == "mms" && host != null) {
                    return host
                }
                
                if (path != null) {
                    return path
                }
            }

            return "unknown"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract sender address", e)
            return "unknown"
        }
    }

    private fun extractTimestamp(intent: Intent): Date {
        try {
            // Try to get timestamp from intent extras
            val timestamp = intent.getLongExtra("timestamp", 0)
            if (timestamp > 0) {
                return Date(timestamp)
            }

            // Fallback to current time
            return Date()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract timestamp, using current time", e)
            return Date()
        }
    }

    private fun extractSubscriptionId(context: Context, intent: Intent): Int? {
        return when {
            intent.extras?.containsKey("android.telephony.extra.SUBSCRIPTION_INDEX") == true -> intent.extras?.getInt(
                "android.telephony.extra.SUBSCRIPTION_INDEX"
            )

            intent.extras?.containsKey("subscription") == true -> intent.extras?.getInt("subscription")
            intent.extras?.containsKey("android.telephony.extra.SLOT_INDEX") == true -> intent.extras?.getInt(
                "android.telephony.extra.SLOT_INDEX"
            )?.let { SubscriptionsHelper.getSubscriptionId(context, it) }

            else -> null
        }
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}