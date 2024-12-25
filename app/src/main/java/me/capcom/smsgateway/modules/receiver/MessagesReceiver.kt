package me.capcom.smsgateway.modules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms.Intents
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

class MessagesReceiver : BroadcastReceiver(), KoinComponent {
    private val receiverSvc: ReceiverService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Intents.getMessagesFromIntent(intent) ?: return
        val firstMessage = messages.first()
        val text = messages.joinToString(separator = "") { it.displayMessageBody }

        receiverSvc.process(
            context,
            InboxMessage(
                id = null,
                address = firstMessage.displayOriginatingAddress,
                body = text,
                date = Date(firstMessage.timestampMillis),
                subscriptionId = extractSubscriptionId(context, intent),
            )
        )
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
}