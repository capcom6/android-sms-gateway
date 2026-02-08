package me.stappmus.messagegateway.modules.webhooks

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.stappmus.messagegateway.domain.ProcessingState
import me.stappmus.messagegateway.modules.events.EventBus
import me.stappmus.messagegateway.modules.events.EventsReceiver
import me.stappmus.messagegateway.modules.messages.events.MessageStateChangedEvent
import me.stappmus.messagegateway.modules.ping.events.PingEvent
import me.stappmus.messagegateway.modules.receiver.events.MmsReceivedEvent
import me.stappmus.messagegateway.modules.receiver.events.SmsDataReceivedEvent
import me.stappmus.messagegateway.modules.receiver.events.SmsReceivedEvent
import me.stappmus.messagegateway.modules.webhooks.domain.WebHookEvent
import me.stappmus.messagegateway.modules.webhooks.payload.SmsEventPayload
import org.koin.core.component.get
import java.util.Date

class EventsReceiver : EventsReceiver() {
    override suspend fun collect(eventBus: EventBus) {
        coroutineScope {
            launch {
                eventBus.collect<PingEvent> {
                    Log.d("EventsReceiver", "Event: $it")

                    get<WebHooksService>().emit(
                        get(),
                        WebHookEvent.SystemPing,
                        mapOf("health" to it.health)
                    )
                }
            }

            launch {
                eventBus.collect<SmsReceivedEvent> { event ->
                    Log.d("EventsReceiver", "Event: $event")
                    get<WebHooksService>().emit(
                        get(),
                        WebHookEvent.SmsReceived,
                        event.payload,
                    )
                }
            }

            launch {
                eventBus.collect<SmsDataReceivedEvent> { event ->
                    Log.d("EventsReceiver", "Event: $event")
                    get<WebHooksService>().emit(
                        get(),
                        WebHookEvent.SmsDataReceived,
                        event.payload,
                    )
                }
            }

            launch {
                eventBus.collect<MmsReceivedEvent> { event ->
                    Log.d("EventsReceiver", "Event: $event")
                    get<WebHooksService>().emit(
                        get(),
                        WebHookEvent.MmsReceived,
                        event.payload,
                    )
                }
            }

            launch {
                eventBus.collect<MessageStateChangedEvent> collectMessageStateChanged@{ event ->
                    Log.d("EventsReceiver", "Event: $event")

                    val webhookEventType = when (event.state) {
                        ProcessingState.Sent -> WebHookEvent.SmsSent
                        ProcessingState.Delivered -> WebHookEvent.SmsDelivered
                        ProcessingState.Failed -> WebHookEvent.SmsFailed
                        else -> return@collectMessageStateChanged
                    }

                    event.phoneNumbers.forEach { phoneNumber ->
                        val payload = when (webhookEventType) {
                            WebHookEvent.SmsSent -> SmsEventPayload.SmsSent(
                                messageId = event.id,
                                phoneNumber = phoneNumber,
                                event.simNumber,
                                partsCount = event.partsCount ?: -1,
                                sentAt = Date(),
                            )

                            WebHookEvent.SmsDelivered -> SmsEventPayload.SmsDelivered(
                                messageId = event.id,
                                phoneNumber = phoneNumber,
                                event.simNumber,
                                deliveredAt = Date(),
                            )

                            WebHookEvent.SmsFailed -> SmsEventPayload.SmsFailed(
                                messageId = event.id,
                                phoneNumber = phoneNumber,
                                event.simNumber,
                                failedAt = Date(),
                                reason = event.error ?: "Unknown",
                            )

                            else -> return@forEach
                        }

                        get<WebHooksService>().emit(
                            get(), webhookEventType, payload
                        )
                    }
                }
            }
        }
    }
}
