package me.stappmus.messagegateway.modules.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import me.stappmus.messagegateway.helpers.SubscriptionsHelper
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.receiver.parsers.MMSParser
import com.klinker.android.send_message.MmsReceivedReceiver
import java.io.File
import kotlin.math.abs
import kotlin.random.Random
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MmsReceiver : BroadcastReceiver(), KoinComponent {
    private val logsService: LogsService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION
            && intent.action != WAP_PUSH_DELIVER_ACTION
        ) {
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
                    "pduSize" to pdu.size,
                    "pduPreview" to pdu.take(64).joinToString("") { "%02x".format(it) },
                )
            )


            val mmsNotification = MMSParser.parseMNotificationInd(pdu)
            val subscriptionId = SubscriptionsHelper.extractSubscriptionId(context, intent)

            triggerMmsDownload(
                context = context,
                transactionId = mmsNotification.transactionId,
                contentLocation = mmsNotification.contentLocation,
                subscriptionId = subscriptionId,
                notificationUri = resolveNotificationUri(bundle),
            )
            Log.d(TAG, "MMS download flow started from ${mmsNotification.from}")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing MMS", e)
        }
    }

    private fun triggerMmsDownload(
        context: Context,
        transactionId: String,
        contentLocation: String?,
        subscriptionId: Int?,
        notificationUri: Uri?,
    ) {
        val sanitizedLocation = contentLocation
            ?.substringBefore('\u0000')
            ?.trim()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

        if (sanitizedLocation == null) {
            Log.w(TAG, "No valid MMS content location for tx=$transactionId")
            return
        }

        val fileName = "download.${abs(Random.nextLong())}.dat"
        val filePath = File(context.cacheDir, fileName).path
        val destinationUri = Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.MmsFileProvider")
            .path(fileName)
            .build()

        val callbackIntent = Intent(context, MmsDownloadResultReceiver::class.java).apply {
            action = ACTION_MMS_DOWNLOAD_COMPLETE
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
            putExtra(MmsReceivedReceiver.EXTRA_FILE_PATH, filePath)
            putExtra(MmsReceivedReceiver.EXTRA_LOCATION_URL, sanitizedLocation)
            putExtra(MmsReceivedReceiver.EXTRA_TRIGGER_PUSH, true)
            putExtra(MmsReceivedReceiver.SUBSCRIPTION_ID, subscriptionId ?: -1)
            notificationUri?.let { putExtra(MmsReceivedReceiver.EXTRA_URI, it.toString()) }
        }

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> PendingIntent.FLAG_MUTABLE
            else -> 0
        }

        try {
            context.grantUriPermission(
                context.packageName,
                destinationUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val smsManager = when {
                subscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    context.getSystemService(SmsManager::class.java)
                        .createForSubscriptionId(subscriptionId)
                }

                subscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 -> {
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                }

                else -> SmsManager.getDefault()
            }

            val callbackPendingIntent = PendingIntent.getBroadcast(
                context,
                transactionId.hashCode(),
                callbackIntent,
                pendingIntentFlags,
            )

            smsManager.downloadMultimediaMessage(
                context,
                sanitizedLocation,
                destinationUri,
                Bundle(),
                callbackPendingIntent,
            )

            Log.i(
                TAG,
                "Triggered MMS download tx=$transactionId via SmsManager"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trigger MMS download tx=$transactionId: ${e.message}")
        }
    }

    private fun resolveNotificationUri(bundle: Bundle): Uri? {
        bundle.getString("uri")
            ?.takeIf { it.startsWith("content://") }
            ?.let { return Uri.parse(it) }

        val messageId = bundle.getLong("messageId", -1L)
        if (messageId > 0L) {
            return Uri.parse("content://mms/$messageId")
        }

        return null
    }

    companion object {
        private const val TAG = "MmsReceiver"
        private const val WAP_PUSH_DELIVER_ACTION = "android.provider.Telephony.WAP_PUSH_DELIVER"
        private const val ACTION_MMS_DOWNLOAD_COMPLETE =
            "me.stappmus.messagegateway.action.MMS_DOWNLOAD_COMPLETE"
        private const val EXTRA_TRANSACTION_ID = "transaction_id"

        private val INSTANCE: MmsReceiver by lazy { MmsReceiver() }

        fun register(context: Context) {
            val filter = IntentFilter().apply {
                addAction(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION)
                addAction(WAP_PUSH_DELIVER_ACTION)
                addDataType("application/vnd.wap.mms-message")
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                INSTANCE,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
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
