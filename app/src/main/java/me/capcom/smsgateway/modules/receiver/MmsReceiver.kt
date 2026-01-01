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
import java.util.Date
import android.net.Uri

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

            Log.d(TAG, "MMS received from ${mmsNotification.from}")

            // De-duplicate - skip if we've processed this transaction ID recently
            val now = System.currentTimeMillis()
            synchronized(processedTransactions) {
                // Clean up old entries
                processedTransactions.entries.removeIf { now - it.value > DUPLICATE_WINDOW_MS }
                
                // Check if already processed
                if (processedTransactions.containsKey(mmsNotification.transactionId)) {
                    Log.d(TAG, "Skipping duplicate MMS with transaction ID: ${mmsNotification.transactionId}")
                    return
                }
                
                // Mark as processed
                processedTransactions[mmsNotification.transactionId] = now
            }

            // Try to read text parts from the MMS using the ContentProvider
            var body: String? = null
            Log.d(TAG, "Attempting to read MMS body from ContentProvider")
            
            try {
                // Increase delay for MMS with images
                Thread.sleep(1500)
                
                val mmsUri = Uri.parse("content://mms")
                val projection = arrayOf("_id", "date", "m_size", "tr_id")
                
                Log.d(TAG, "Querying MMS provider for transaction ID: ${mmsNotification.transactionId}")
                
                // Try to match by transaction ID first
                var cursor = context.contentResolver.query(
                    mmsUri,
                    projection,
                    "tr_id = ?",
                    arrayOf(mmsNotification.transactionId),
                    null
                )
                
                // If not found by transaction ID, fall back to latest message
                if (cursor == null || cursor.count == 0) {
                    cursor?.close()
                    Log.d(TAG, "Transaction ID not found, trying latest message")
                    cursor = context.contentResolver.query(
                        mmsUri,
                        projection,
                        null,
                        null,
                        "_id DESC LIMIT 1"
                    )
                }
                
                if (cursor != null) {
                    Log.d(TAG, "Found ${cursor.count} recent MMS messages")
                    
                    if (cursor.count > 0 && cursor.moveToFirst()) {
                        val mmsId = cursor.getString(cursor.getColumnIndex("_id"))
                        Log.d(TAG, "Using latest MMS ID=$mmsId")
                        
                        val partsUri = Uri.parse("content://mms/$mmsId/part")
                        val partProjection = arrayOf("_id", "ct", "text")
                        val partsCursor = context.contentResolver.query(partsUri, partProjection, null, null, null)
                        
                        if (partsCursor != null) {
                            Log.d(TAG, "Found ${partsCursor.count} parts for MMS ID $mmsId")
                            val ctIndex = partsCursor.getColumnIndex("ct")
                            val textIndex = partsCursor.getColumnIndex("text")
                            
                            while (partsCursor.moveToNext()) {
                                val ct = if (ctIndex >= 0) partsCursor.getString(ctIndex) else ""
                                Log.d(TAG, "Part content-type: $ct")
                                
                                if (ct.startsWith("text")) {
                                    val text = if (textIndex >= 0) partsCursor.getString(textIndex) else null
                                    if (!text.isNullOrEmpty()) {
                                        body = if (body == null) text else body + "\n" + text
                                        Log.d(TAG, "Extracted text: ${text.take(100)}")
                                    }
                                }
                            }
                            partsCursor.close()
                        }
                    }
                    cursor.close()
                } else {
                    Log.w(TAG, "Query returned null cursor")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read MMS parts: ${e.message}", e)
            }
            
            // Debug log about extracted body
            logsService.insert(
                LogEntry.Priority.DEBUG,
                MODULE_NAME,
                "MMS body extraction",
                mapOf(
                    "bodyFound" to (body != null),
                    "bodyLength" to (body?.length ?: 0),
                    "bodyPreview" to (body?.take(200))
                )
            )
            Log.d("MmsReceiver", "MMS body extraction: found=${body != null} length=${body?.length ?: 0} preview=${body?.take(200) ?: "null"}")

            val mmsMessage = InboxMessage.Mms(
                messageId = mmsNotification.messageId,
                transactionId = mmsNotification.transactionId,
                subject = mmsNotification.subject,
                size = mmsNotification.messageSize,
                contentClass = mmsNotification.contentClass?.name,
                body = body,
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
        private const val DUPLICATE_WINDOW_MS = 60000L // 1 minute

        private val INSTANCE: MmsReceiver by lazy { MmsReceiver() }
        private val processedTransactions = mutableMapOf<String, Long>()

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