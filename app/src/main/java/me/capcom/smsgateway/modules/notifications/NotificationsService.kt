package me.capcom.smsgateway.modules.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import me.capcom.smsgateway.R

class NotificationsService(
    context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    private val icons = mapOf(
        NOTIFICATION_ID_LOCAL_SERVICE to R.drawable.notif_server,
        NOTIFICATION_ID_SEND_WORKER to R.drawable.notif_send,
        NOTIFICATION_ID_WEBHOOK_WORKER to R.drawable.notif_webhook,
        NOTIFICATION_ID_PING_SERVICE to R.drawable.notif_ping,
    )

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.sms_gateway)
            val descriptionText = context.getString(R.string.local_sms_gateway_notifications)
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    fun makeNotification(context: Context, id: Int, contentText: String): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getText(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(icons[id] ?: R.drawable.ic_sms)
            .build()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "sms-gateway"

        const val NOTIFICATION_ID_LOCAL_SERVICE = 1
        const val NOTIFICATION_ID_SEND_WORKER = 2
        const val NOTIFICATION_ID_WEBHOOK_WORKER = 3
        const val NOTIFICATION_ID_PING_SERVICE = 4
    }
}