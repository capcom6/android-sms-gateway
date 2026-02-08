package me.stappmus.messagegateway.modules.orchestrator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.stappmus.messagegateway.modules.events.EventBus
import me.stappmus.messagegateway.modules.events.ExternalEvent
import me.stappmus.messagegateway.modules.events.ExternalEventType
import me.stappmus.messagegateway.modules.gateway.events.MessageEnqueuedEvent
import me.stappmus.messagegateway.modules.gateway.events.SettingsUpdatedEvent
import me.stappmus.messagegateway.modules.gateway.events.WebhooksUpdatedEvent
import me.stappmus.messagegateway.modules.receiver.events.MessagesExportRequestedEvent

class EventsRouter(
    private val eventBus: EventBus
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun route(event: ExternalEvent) {
        scope.launch {
            when (event.type) {
                ExternalEventType.MessageEnqueued ->
                    eventBus.emit(
                        MessageEnqueuedEvent()
                    )

                ExternalEventType.WebhooksUpdated ->
                    eventBus.emit(
                        WebhooksUpdatedEvent()
                    )

                ExternalEventType.MessagesExportRequested ->
                    eventBus.emit(
                        MessagesExportRequestedEvent.withPayload(
                            requireNotNull(event.data)
                        )
                    )

                ExternalEventType.SettingsUpdated ->
                    eventBus.emit(
                        SettingsUpdatedEvent()
                    )
            }
        }
    }
}