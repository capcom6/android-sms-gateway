package me.capcom.smsgateway.modules.gateway

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.events.EventsReceiver
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.gateway.events.MessageCancelledEvent
import me.capcom.smsgateway.modules.gateway.events.MessageEnqueuedEvent
import me.capcom.smsgateway.modules.gateway.events.SettingsUpdatedEvent
import me.capcom.smsgateway.modules.gateway.events.WebhooksUpdatedEvent
import me.capcom.smsgateway.modules.gateway.services.SSEForegroundService
import me.capcom.smsgateway.modules.gateway.workers.PullMessagesWorker
import me.capcom.smsgateway.modules.gateway.workers.SendStateWorker
import me.capcom.smsgateway.modules.gateway.workers.SettingsUpdateWorker
import me.capcom.smsgateway.modules.gateway.workers.WebhooksUpdateWorker
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import me.capcom.smsgateway.modules.ping.events.PingEvent
import org.koin.core.component.get

class EventsReceiver : EventsReceiver() {

    private val settings = get<GatewaySettings>()

    override suspend fun collect(eventBus: EventBus) {
        coroutineScope {
            launch {
                Log.d("EventsReceiver", "launched MessageEnqueuedEvent")
                eventBus.collect<MessageEnqueuedEvent> { event ->
                    try {
                        Log.d("EventsReceiver", "Event: $event")

                        if (!settings.enabled) return@collect

                        PullMessagesWorker.start(get())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // per-collector isolation: an exception here must not cancel
                        // the coroutineScope and kill sibling collectors
                        Log.e("EventsReceiver", "MessageEnqueuedEvent collector failed", e)
                    }
                }
            }
            launch {
                Log.d("EventsReceiver", "launched MessageStateChangedEvent")
                val allowedSources = setOf(EntitySource.Cloud, EntitySource.Gateway)
                eventBus.collect<MessageStateChangedEvent> { event ->
                    try {
                        Log.d("EventsReceiver", "Event: $event")

                        if (!settings.enabled) return@collect

                        if (event.source !in allowedSources) return@collect

                        SendStateWorker.start(get(), event.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // per-collector isolation: an exception here must not cancel
                        // the coroutineScope and kill sibling collectors
                        Log.e("EventsReceiver", "MessageStateChangedEvent collector failed", e)
                    }
                }
            }

            launch {
                Log.d("EventsReceiver", "launched PingEvent")
                eventBus.collect<PingEvent> {
                    try {
                        Log.d("EventsReceiver", "Event: $it")

                        if (!settings.enabled) return@collect

                        PullMessagesWorker.start(get())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // per-collector isolation: an exception here must not cancel
                        // the coroutineScope and kill sibling collectors
                        Log.e("EventsReceiver", "PingEvent collector failed", e)
                    }
                }
            }

            launch {
                Log.d("EventsReceiver", "launched WebhooksUpdatedEvent")
                eventBus.collect<WebhooksUpdatedEvent> {
                    try {
                        Log.d("EventsReceiver", "Event: $it")

                        if (!settings.enabled) return@collect

                        WebhooksUpdateWorker.start(get())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // per-collector isolation: an exception here must not cancel
                        // the coroutineScope and kill sibling collectors
                        Log.e("EventsReceiver", "WebhooksUpdatedEvent collector failed", e)
                    }
                }
            }

            launch {
                Log.d("EventsReceiver", "launched SettingsUpdatedEvent")
                eventBus.collect<SettingsUpdatedEvent> {
                    try {
                        Log.d("EventsReceiver", "Event: $it")

                        if (!settings.enabled) return@collect

                        SettingsUpdateWorker.start(get())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // per-collector isolation: an exception here must not cancel
                        // the coroutineScope and kill sibling collectors
                        Log.e("EventsReceiver", "SettingsUpdatedEvent collector failed", e)
                    }
                }
            }

            launch {
                Log.d("EventsReceiver", "launched DeviceRegisteredEvent")
                eventBus.collect<DeviceRegisteredEvent> {
                    try {
                        Log.d("EventsReceiver", "Event: $it")

                        if (!settings.enabled) return@collect
                        if (settings.fcmToken != null) return@collect

                        SSEForegroundService.start(get())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // per-collector isolation: an exception here must not cancel
                        // the coroutineScope and kill sibling collectors
                        Log.e("EventsReceiver", "DeviceRegisteredEvent collector failed", e)
                    }
                }
            }

            launch {
                Log.d("EventsReceiver", "launched MessageCancelledEvent")
                eventBus.collect<MessageCancelledEvent> { event ->
                    try {
                        Log.d("EventsReceiver", "Event: $event")

                        if (!settings.enabled) return@collect

                        try {
                            get<MessagesService>().cancelMessage(event.messageId)
                        } catch (_: IllegalArgumentException) {
                            // message not found locally — nothing to cancel
                        } catch (_: IllegalStateException) {
                            // message not in Pending state — already sent/cancelled
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // per-collector isolation: a non-IAE/ISE exception must not
                        // cancel the coroutineScope and kill sibling collectors
                        Log.e("EventsReceiver", "MessageCancelledEvent collector failed", e)
                    }
                }
            }
        }

    }
}