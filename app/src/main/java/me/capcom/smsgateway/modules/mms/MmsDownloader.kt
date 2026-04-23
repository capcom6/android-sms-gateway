package me.capcom.smsgateway.modules.mms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.receivers.EventsReceiver
import java.io.File

/**
 * Actively downloads an MMS given the `Content-Location` from an incoming
 * M-Notification.ind (WAP push). Requires the app to hold
 * `WRITE_SMS` / `RECEIVE_WAP_PUSH` — typically granted to the default SMS app.
 *
 * Result is delivered as `EventsReceiver.ACTION_MMS_DOWNLOADED`. The downloaded
 * bytes are written to a FileProvider-backed URI we pass as `contentUri`; once
 * the download succeeds the system MMS provider is also populated, so the
 * existing `MmsContentObserver` will pick up and index the message.
 */
class MmsDownloader(private val context: Context) {

    fun download(
        messageId: String,
        transactionId: String,
        contentLocation: String,
        subscriptionId: Int?,
    ) {
        require(contentLocation.isNotBlank()) { "contentLocation is required" }

        val file = prepareOutputFile(messageId)
        val uri = FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            file
        )

        context.grantUriPermission(
            "com.android.phone", uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.grantUriPermission(
            "com.android.mms.service", uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val downloadedIntent = PendingIntent.getBroadcast(
            context,
            messageId.hashCode(),
            Intent(
                EventsReceiver.ACTION_MMS_DOWNLOADED,
                Uri.parse("mmsgw:$transactionId"),
                context,
                EventsReceiver::class.java
            ).apply {
                putExtra(EventsReceiver.EXTRA_MESSAGE_ID, messageId)
                putExtra(EventsReceiver.EXTRA_MMS_URI, uri.toString())
                putExtra(EventsReceiver.EXTRA_PDU_PATH, file.absolutePath)
                if (subscriptionId != null) {
                    putExtra(EventsReceiver.EXTRA_SUBSCRIPTION_ID, subscriptionId)
                }
                flags = Intent.FLAG_RECEIVER_FOREGROUND
            },
            pendingIntentFlags()
        )

        val manager = getSmsManager(subscriptionId)
        manager.downloadMultimediaMessage(
            context,
            contentLocation,
            uri,
            null, // configOverrides
            downloadedIntent
        )
    }

    private fun prepareOutputFile(messageId: String): File {
        val dir = File(context.filesDir, "mms-in").apply { mkdirs() }
        val safeId = messageId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val file = File(dir, "$safeId-retrieve.pdu")
        if (file.exists()) file.delete()
        file.createNewFile()
        return file
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(subscriptionId: Int?): SmsManager {
        return when {
            subscriptionId == null || subscriptionId < 0 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)

            else -> SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }
}
