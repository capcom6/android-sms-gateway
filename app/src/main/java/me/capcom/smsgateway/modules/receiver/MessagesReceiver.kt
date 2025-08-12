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
        if (intent.action != Intents.SMS_RECEIVED_ACTION
            && intent.action != Intents.DATA_SMS_RECEIVED_ACTION
        ) {
            return
        }

        val messages = Intents.getMessagesFromIntent(intent) ?: return
        val isDataMessage = intent.action == Intents.DATA_SMS_RECEIVED_ACTION
        val firstMessage = messages.first()
//        val text = messages.joinToString(separator = "") { it.displayMessageBody }

        val inboxMessage = when (isDataMessage) {
            false -> InboxMessage.Text(
                messages.joinToString(separator = "") { it.displayMessageBody },
                firstMessage.displayOriginatingAddress,
                Date(firstMessage.timestampMillis),
                SubscriptionsHelper.extractSubscriptionId(context, intent)
            )

            true -> InboxMessage.Data(
                firstMessage.userData,
                firstMessage.displayOriginatingAddress,
                Date(firstMessage.timestampMillis),
                SubscriptionsHelper.extractSubscriptionId(context, intent)
            )
        }

        receiverSvc.process(
            context,
            inboxMessage
        )
    }
}