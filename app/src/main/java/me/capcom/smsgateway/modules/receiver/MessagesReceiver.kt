package me.capcom.smsgateway.modules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms.Intents
import android.util.Log

class MessagesReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Intents.getMessagesFromIntent(intent)?.onEach {
            Log.d(
                "SMSReceiver",
                "Message " + it.messageBody + ", from " + it.displayOriginatingAddress
            )
        }
    }
}