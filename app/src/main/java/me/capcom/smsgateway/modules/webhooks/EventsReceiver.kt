package me.capcom.smsgateway.modules.webhooks

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.events.EventsReceiver
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import me.capcom.smsgateway.modules.ping.events.PingEvent
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import me.capcom.smsgateway.modules.webhooks.payload.SmsSentPayload
import org.koin.core.component.get
import java.util.Date

class EventsReceiver : EventsReceiver() {
    override suspend fun collect(eventBus: EventBus) {
        coroutineScope {
            launch {
                eventBus.collect<PingEvent> {
                    Log.d("EventsReceiver", "Event: $it")

                    get<WebHooksService>().emit(WebHookEvent.SystemPing, object {})
                }
            }

            launch {
                eventBus.collect<MessageStateChangedEvent> { event ->
                    Log.d("EventsReceiver", "Event: $event")

                    val webhookEventType = when (event.state) {
                        ProcessingState.Sent -> WebHookEvent.SmsSent
                        else -> return@collect
                    }

                    event.phoneNumbers.forEach { phoneNumber ->
                        get<WebHooksService>().emit(
                            webhookEventType, SmsSentPayload(
                                messageId = event.id,
                                phoneNumber = phoneNumber,
                                sentAt = Date(),
                            )
                        )
                    }
                }
            }
        }
    }
}