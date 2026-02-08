package me.stappmus.messagegateway.modules.receiver

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.stappmus.messagegateway.modules.events.EventBus
import me.stappmus.messagegateway.modules.events.EventsReceiver
import me.stappmus.messagegateway.modules.receiver.events.MessagesExportRequestedEvent
import org.koin.core.component.get
import org.koin.core.component.inject

class EventsReceiver : EventsReceiver() {
    private val service by inject<ReceiverService>()

    override suspend fun collect(eventBus: EventBus) {
        coroutineScope {
            launch {
                Log.d("EventsReceiver", "launched MessagesExportRequestedEvent")
                eventBus.collect<MessagesExportRequestedEvent> { event ->
                    Log.d("EventsReceiver", "Event: $event")

                    service.export(
                        get(),
                        event.since to event.until,
                    )
                }
            }
        }
    }
}