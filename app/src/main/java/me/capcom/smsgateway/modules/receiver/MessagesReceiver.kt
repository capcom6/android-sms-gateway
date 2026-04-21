package me.capcom.smsgateway.modules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony.Sms.Intents
import android.util.Log
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

/**
 * Handles binary/data SMS on port 53739 (`DATA_SMS_RECEIVED_ACTION`). Regular
 * text SMS ingest is driven by `SmsContentObserver` — the observer path works
 * across all carriers, including those where a vendor CarrierMessagingService
 * swallows `SMS_DELIVER` and `SMS_RECEIVED` (e.g. Verizon on Pixel).
 */
class MessagesReceiver : BroadcastReceiver(), KoinComponent {
    private val receiverSvc: ReceiverService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intents.DATA_SMS_RECEIVED_ACTION) return

        val firstMessage = Intents.getMessagesFromIntent(intent)?.firstOrNull() ?: return

        val inboxMessage = InboxMessage.Data(
            firstMessage.userData,
            firstMessage.displayOriginatingAddress,
            Date(firstMessage.timestampMillis),
            SubscriptionsHelper.extractSubscriptionId(context, intent),
        )

        receiverSvc.process(
            context,
            inboxMessage,
            true,
        )
    }

    companion object {
        private const val TAG = "MessagesReceiver"

        private val INSTANCE: MessagesReceiver by lazy { MessagesReceiver() }

        fun register(context: Context) {
            unregister(context)

            val dataFilter = IntentFilter().apply {
                addAction(Intents.DATA_SMS_RECEIVED_ACTION)
                addDataScheme("sms")
                addDataAuthority("*", "53739")
            }
            context.registerReceiver(
                INSTANCE,
                dataFilter
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