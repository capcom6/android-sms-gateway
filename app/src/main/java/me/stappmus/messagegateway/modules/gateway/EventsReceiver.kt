package me.stappmus.messagegateway.modules.gateway

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.modules.events.EventBus
import me.stappmus.messagegateway.modules.events.EventsReceiver
import me.stappmus.messagegateway.modules.gateway.events.DeviceRegisteredEvent
import me.stappmus.messagegateway.modules.gateway.events.MessageEnqueuedEvent
import me.stappmus.messagegateway.modules.gateway.events.SettingsUpdatedEvent
import me.stappmus.messagegateway.modules.gateway.events.WebhooksUpdatedEvent
import me.stappmus.messagegateway.modules.gateway.services.SSEForegroundService
import me.stappmus.messagegateway.modules.gateway.workers.PullMessagesWorker
import me.stappmus.messagegateway.modules.gateway.workers.SendStateWorker
import me.stappmus.messagegateway.modules.gateway.workers.SettingsUpdateWorker
import me.stappmus.messagegateway.modules.gateway.workers.WebhooksUpdateWorker
import me.stappmus.messagegateway.modules.messages.events.MessageStateChangedEvent
import me.stappmus.messagegateway.modules.ping.events.PingEvent
import org.koin.core.component.get

class EventsReceiver : EventsReceiver() {

    private val settings = get<GatewaySettings>()

    override suspend fun collect(eventBus: EventBus) {
        coroutineScope {
            launch {
                Log.d("EventsReceiver", "launched MessageEnqueuedEvent")
                eventBus.collect<MessageEnqueuedEvent> collectMessageEnqueued@{ event ->
                    Log.d("EventsReceiver", "Event: $event")

                    if (!settings.enabled) return@collectMessageEnqueued

                    PullMessagesWorker.start(get())
                }
            }
            launch {
                Log.d("EventsReceiver", "launched MessageStateChangedEvent")
                val allowedSources = setOf(EntitySource.Cloud, EntitySource.Gateway)
                eventBus.collect<MessageStateChangedEvent> collectMessageStateChanged@{ event ->
                    Log.d("EventsReceiver", "Event: $event")

                    if (!settings.enabled) return@collectMessageStateChanged

                    if (event.source !in allowedSources) return@collectMessageStateChanged

                    SendStateWorker.start(get(), event.id)
                }
            }

            launch {
                Log.d("EventsReceiver", "launched PingEvent")
                eventBus.collect<PingEvent> collectPing@{
                    Log.d("EventsReceiver", "Event: $it")

                    if (!settings.enabled) return@collectPing

                    PullMessagesWorker.start(get())
                }
            }

            launch {
                Log.d("EventsReceiver", "launched WebhooksUpdatedEvent")
                eventBus.collect<WebhooksUpdatedEvent> collectWebhooksUpdated@{
                    Log.d("EventsReceiver", "Event: $it")

                    if (!settings.enabled) return@collectWebhooksUpdated

                    WebhooksUpdateWorker.start(get())
                }
            }

            launch {
                Log.d("EventsReceiver", "launched SettingsUpdatedEvent")
                eventBus.collect<SettingsUpdatedEvent> collectSettingsUpdated@{
                    Log.d("EventsReceiver", "Event: $it")

                    if (!settings.enabled) return@collectSettingsUpdated

                    SettingsUpdateWorker.start(get())
                }
            }

            launch {
                Log.d("EventsReceiver", "launched DeviceRegisteredEvent")
                eventBus.collect<DeviceRegisteredEvent> collectDeviceRegistered@{
                    Log.d("EventsReceiver", "Event: $it")

                    if (!settings.enabled) return@collectDeviceRegistered
                    if (settings.fcmToken != null) return@collectDeviceRegistered

                    SSEForegroundService.start(get())
                }
            }
        }

    }
}
