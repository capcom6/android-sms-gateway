package me.capcom.smsgateway.modules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms.Intents
import me.capcom.smsgateway.modules.receiver.events.MessageReceivedEvent
import me.capcom.smsgateway.modules.webhooks.WebHooksService
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

class MessagesReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Intents.getMessagesFromIntent(intent) ?: return
        val firstMessage = messages.first()
        val text = messages.joinToString(separator = "") { it.displayMessageBody }

        val event = MessageReceivedEvent(
            message = text,
            phoneNumber = firstMessage.displayOriginatingAddress,
            receivedAt = Date(firstMessage.timestampMillis),
        )

        webHooksService.emit(WebHookEvent.SmsReceived, event)
    }

    private val webHooksService: WebHooksService by inject()
}