package me.capcom.smsgateway.modules.receiver

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.events.EventsReceiver
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.receiver.events.MessagesExportRequestedEvent
import org.koin.core.component.get
import org.koin.core.component.inject

class EventsReceiver : EventsReceiver() {
    private val service by inject<ReceiverService>()
    private val gatewaySettings by inject<GatewaySettings>()

    override suspend fun collect(eventBus: EventBus) {
        coroutineScope {
            launch {
                Log.d("EventsReceiver", "launched MessagesExportRequestedEvent")
                eventBus.collect<MessagesExportRequestedEvent> { event ->
                    Log.d("EventsReceiver", "Event: $event")

                    // This receiver is not stopped by GatewayService.stop(), so it keeps
                    // collecting after unregister. Drop the command unless the device is
                    // still registered with the cloud server.
                    if (gatewaySettings.registrationInfo == null) {
                        Log.d("EventsReceiver", "Export request ignored: device is not registered")
                        return@collect
                    }

                    service.export(
                        context = get(),
                        period = event.since to event.until,
                        messageTypes = event.messageTypes,
                        webhookDelivery = event.webhookDelivery
                    )
                }
            }
        }
    }
}
