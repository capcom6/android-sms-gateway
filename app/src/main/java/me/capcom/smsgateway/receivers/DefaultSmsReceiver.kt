package me.capcom.smsgateway.receivers

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.receiver.ReceiverService
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

/**
 * Required static receiver when the app is the default SMS app. The system
 * only delivers `SMS_DELIVER_ACTION` to the default app, and expects that app
 * to persist the message into the Telephony provider so other apps can see it.
 */
class DefaultSmsReceiver : BroadcastReceiver(), KoinComponent {
    private val receiverSvc: ReceiverService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = messages.firstOrNull() ?: return
        val body = messages.joinToString("") { it.displayMessageBody }
        val address = first.displayOriginatingAddress
        val date = Date(first.timestampMillis)
        val subId = SubscriptionsHelper.extractSubscriptionId(context, intent)

        // Persist into the system Telephony provider so messaging apps
        // (and our MmsContentObserver code path for uniformity) can see it.
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.Inbox.ADDRESS, address)
                put(Telephony.Sms.Inbox.BODY, body)
                put(Telephony.Sms.Inbox.DATE, first.timestampMillis)
                put(Telephony.Sms.Inbox.DATE_SENT, first.timestampMillis)
                put(Telephony.Sms.Inbox.READ, 0)
                put(Telephony.Sms.Inbox.SEEN, 0)
                put(Telephony.Sms.Inbox.SUBSCRIPTION_ID, subId ?: -1)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to persist incoming SMS", e)
        }

        receiverSvc.process(
            context,
            InboxMessage.Text(body, address, date, subId),
            true,
        )
    }

    companion object {
        private const val TAG = "DefaultSmsReceiver"
    }
}
