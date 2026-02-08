package me.stappmus.messagegateway.modules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony.Sms.Intents
import android.util.Log
import me.stappmus.messagegateway.helpers.SubscriptionsHelper
import me.stappmus.messagegateway.modules.receiver.data.InboxMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

class MessagesReceiver : BroadcastReceiver(), KoinComponent {
    private val receiverSvc: ReceiverService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intents.SMS_RECEIVED_ACTION
            && intent.action != SMS_DELIVER_ACTION
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

    companion object {
        private const val TAG = "MessagesReceiver"
        private const val SMS_DELIVER_ACTION = "android.provider.Telephony.SMS_DELIVER"

        private val INSTANCE: MessagesReceiver by lazy { MessagesReceiver() }

        fun register(context: Context) {
            unregister(context)

            val dataFilter = IntentFilter().apply {
                addAction(Intents.DATA_SMS_RECEIVED_ACTION)
                addDataScheme("sms")
                addDataAuthority("*", "53739")
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                INSTANCE,
                dataFilter,
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
