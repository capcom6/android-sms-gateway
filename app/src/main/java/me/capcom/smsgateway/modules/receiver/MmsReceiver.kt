package me.capcom.smsgateway.modules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

            // Register a ContentObserver to wait for the MMS to appear in the ContentProvider
            val subscriptionId = SubscriptionsHelper.extractSubscriptionId(context, intent)
            watchForMms(
                context,
                mmsNotification.transactionId,
                mmsNotification.messageId,
                mmsNotification.subject,
                mmsNotification.messageSize,
                mmsNotification.contentClass?.name,
                mmsNotification.from.substringBefore('/'),
                mmsNotification.date ?: Date(),
                subscriptionId
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing MMS", e)
        }
    }

    private fun watchForMms(
        context: Context,
        transactionId: String,
        messageId: String?,
        subject: String?,
        size: Long?,
        contentClass: String?,
        address: String,
        date: Date,
        subscriptionId: Int?
    ) {
        val mmsUri = Uri.parse("content://mms")
        val handler = Handler(Looper.getMainLooper())
        val timeoutMs = 30000L // 30 second timeout
        val startTime = System.currentTimeMillis()

        // Get the current highest MMS ID so we know to wait for a newer one
        val currentMaxId = getCurrentMaxMmsId(context)
        Log.d(TAG, "ContentObserver: Current max MMS ID is $currentMaxId, waiting for newer MMS from $address")

        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (attemptBodyExtractionAndProcess(context, address, currentMaxId, startTime, timeoutMs, 
                    transactionId, messageId, subject, size, contentClass, date, subscriptionId, this, handler)) {
                    // Successfully processed or timed out
                }
            }
        }

        // Register the observer
        context.contentResolver.registerContentObserver(mmsUri, true, observer)
        
        // Also do an immediate check in case the MMS is already there
        handler.post {
            attemptBodyExtractionAndProcess(context, address, currentMaxId, startTime, timeoutMs,
                transactionId, messageId, subject, size, contentClass, date, subscriptionId, observer, handler)
        }

        // Set a timeout to process even if we don't get the body (should not hit)
        handler.postDelayed({
            context.contentResolver.unregisterContentObserver(observer)
            Log.w(TAG, "ContentObserver: Timeout reached for address: $address, processing without body")
            val body = tryExtractMmsByAddressAndDate(context, address, 0L)
            processMmsMessage(context, transactionId, messageId, subject, size, contentClass, body, address, date, subscriptionId)
        }, timeoutMs)
    }

    private fun attemptBodyExtractionAndProcess(
        context: Context,
        address: String,
        minMmsId: Long,
        startTime: Long,
        timeoutMs: Long,
        transactionId: String,
        messageId: String?,
        subject: String?,
        size: Long?,
        contentClass: String?,
        date: Date,
        subscriptionId: Int?,
        observer: ContentObserver,
        handler: Handler
    ): Boolean {
        val body = tryExtractMmsByAddressAndDate(context, address, minMmsId)
        if (body != null || System.currentTimeMillis() - startTime > timeoutMs) {
            // Found the MMS or timed out - unregister and process
            context.contentResolver.unregisterContentObserver(observer)
            handler.removeCallbacksAndMessages(null)
            
            if (body == null) {
                Log.w(TAG, "ContentObserver: Timed out waiting for MMS body, processing without body")
            }
            processMmsMessage(context, transactionId, messageId, subject, size, contentClass, body, address, date, subscriptionId)
            return true
        }
        return false
    }

    private fun getCurrentMaxMmsId(context: Context): Long {
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://mms"),
                arrayOf("_id"),
                null,
                null,
                "_id DESC LIMIT 1"
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex("_id")
                if (idIndex >= 0) {
                    val maxId = cursor.getLong(idIndex)
                    cursor.close()
                    return maxId
                }
                cursor.close()
            }
            cursor?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error getting max MMS ID: ${e.message}", e)
        }
        return 0L
    }

    private fun tryExtractMmsByAddressAndDate(context: Context, address: String, minMmsId: Long): String? {
        try {
            val mmsUri = Uri.parse("content://mms")
            
            // Get MMS messages with ID greater than minMmsId (newer messages)
            val cursor = context.contentResolver.query(
                mmsUri,
                arrayOf("_id", "date"),
                "_id > ?",
                arrayOf(minMmsId.toString()),
                "_id DESC LIMIT 10"
            )
            
            Log.d(TAG, "Looking for MMS with ID > $minMmsId from address=$address, found ${cursor?.count ?: 0} candidates")
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex("_id")
                val dateIndex = cursor.getColumnIndex("date")
                
                if (idIndex < 0 || dateIndex < 0) {
                    Log.w(TAG, "Required column not found in MMS query")
                    cursor.close()
                    return null
                }
                
                do {
                    val mmsId = cursor.getLong(idIndex)
                    val mmsDate = cursor.getLong(dateIndex)
                    
                    // Check if this MMS is from the expected address.  This is to prevent the case
                    // where 2 or more MMS is received at the same time and sending incorrect one.
                    // If address data not populated, wait a bit and retry
                    var senderMatch = checkMmsSender(context, mmsId.toString(), address)
                    if (!senderMatch) {
                        // Wait for address data to be populated
                        Thread.sleep(ADDRESS_DATA_RETRY_DELAY_MS)
                        senderMatch = checkMmsSender(context, mmsId.toString(), address)
                    }
                    
                    if (senderMatch) {
                        cursor.close()
                        Log.d(TAG, "Found matching MMS ID=$mmsId for address=$address")
                        return extractBodyFromMmsId(context, mmsId.toString())
                    }
                } while (cursor.moveToNext())
                cursor.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting MMS by address: ${e.message}", e)
        }
        return null
    }

    private fun checkMmsSender(context: Context, mmsId: String, expectedAddress: String): Boolean {
        try {
            val addrUri = Uri.parse("content://mms/$mmsId/addr")
            val cursor = context.contentResolver.query(
                addrUri,
                arrayOf("address", "type"),
                null,
                null,
                null
            )
            
            if (cursor != null) {
                val hasData = cursor.count > 0
                val typeIndex = cursor.getColumnIndex("type")
                val addressIndex = cursor.getColumnIndex("address")
                
                if (typeIndex < 0 || addressIndex < 0) {
                    Log.w(TAG, "Required column not found in MMS address query")
                    cursor.close()
                    return false
                }
                
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(typeIndex)
                    val addr = cursor.getString(addressIndex)
                    
                    // Type 137 = FROM (sender)
                    if (type == MMS_ADDR_TYPE_FROM && addr != null) {
                        cursor.close()
                        // Normalize addresses for comparison
                        val normalizedExpected = expectedAddress.replace(Regex("[^0-9]"), "")
                        val normalizedActual = addr.replace(Regex("[^0-9]"), "")
                        return normalizedActual.endsWith(normalizedExpected) || normalizedExpected.endsWith(normalizedActual)
                    }
                }
                cursor.close()
                
                // If cursor had no rows, addresses haven't been populated yet
                if (!hasData) {
                    Log.d(TAG, "MMS $mmsId has no address data yet")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking MMS sender: ${e.message}", e)
        }
        return false
    }

    private fun extractBodyFromMmsId(context: Context, mmsId: String): String? {
        var body: String? = null
        try {
            val partsUri = Uri.parse("content://mms/$mmsId/part")
            val partProjection = arrayOf("_id", "ct", "text")
            val partsCursor = context.contentResolver.query(partsUri, partProjection, null, null, null)
            
            if (partsCursor != null) {
                val ctIndex = partsCursor.getColumnIndex("ct")
                val textIndex = partsCursor.getColumnIndex("text")
                
                if (ctIndex < 0 || textIndex < 0) {
                    Log.w(TAG, "Required column not found in MMS parts query")
                    partsCursor.close()
                    return null
                }
                
                while (partsCursor.moveToNext()) {
                    val ct = partsCursor.getString(ctIndex)
                    
                    if (ct.startsWith("text")) {
                        val text = partsCursor.getString(textIndex)
                        if (!text.isNullOrEmpty()) {
                            body = if (body == null) text else body + "\n" + text
                        }
                    }
                }
                partsCursor.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting body from MMS ID $mmsId: ${e.message}", e)
        }
        return body
    }

    private fun processMmsMessage(
        context: Context,
        transactionId: String,
        messageId: String?,
        subject: String?,
        size: Long?,
        contentClass: String?,
        body: String?,
        address: String,
        date: Date,
        subscriptionId: Int?
    ) {
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
        val mmsMessage = InboxMessage.Mms(
            messageId = messageId ?: transactionId,
            transactionId = transactionId,
            subject = subject,
            size = size ?: 0L,
            contentClass = contentClass,
            body = body,
            address = address,
            date = date,
            subscriptionId = subscriptionId,
        )

        // Process the message using the existing ReceiverService
        receiverSvc.process(context, mmsMessage)
    }

    companion object {
        private const val TAG = "MmsReceiver"
        private const val ADDRESS_DATA_RETRY_DELAY_MS = 500L // Wait for address data to be populated
        private const val MMS_ADDR_TYPE_FROM = 137 // Sender address type in MMS addr table

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