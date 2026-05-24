package me.capcom.smsgateway.receivers

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import me.capcom.smsgateway.helpers.SubscriptionsHelper

/**
 * Required static receiver when the app is the default SMS app. The system
 * only delivers `SMS_DELIVER_ACTION` to the default app, and expects that
 * app to persist the message into the Telephony provider so other apps can
 * see it.
 *
 * Processing (gateway DB + webhooks) is driven from `SmsContentObserver`,
 * not from here — that path is carrier-agnostic and also handles the
 * (common) case where a vendor CarrierMessagingService swallows the
 * `SMS_DELIVER` broadcast but still writes the row into `content://sms/inbox`
 * (e.g. Verizon on Pixel).
 */
class DefaultSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = messages.firstOrNull() ?: return
        val body = messages.joinToString("") { it.displayMessageBody }
        val address = first.displayOriginatingAddress
        val subId = SubscriptionsHelper.extractSubscriptionId(context, intent)

        // Persist into the system Telephony provider so messaging apps can
        // see the message. The subsequent content-provider notification is
        // picked up by SmsContentObserver, which drives processing.
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
    }

    companion object {
        private const val TAG = "DefaultSmsReceiver"
    }
}
