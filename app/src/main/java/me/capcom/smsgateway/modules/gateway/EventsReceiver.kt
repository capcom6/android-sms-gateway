package me.capcom.smsgateway.modules.gateway

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.workers.SendStateWorker
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

class EventsReceiver : KoinComponent {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val settings by inject<GatewaySettings>()

    private val eventBus = get<EventBus>()

    private var job: Job? = null

    fun start() {
        stop()

        this.job = scope.launch {
            val allowedSources = setOf(EntitySource.Cloud, EntitySource.Gateway)
            eventBus.collect<MessageStateChangedEvent> { event ->
                Log.d("EventsReceiver", "Event: $event")

                if (!settings.enabled) return@collect

                if (event.source !in allowedSources) return@collect

                SendStateWorker.start(get(), event.id)
            }
        }
    }

    fun stop() {
        this.job?.cancel()
        this.job = null
    }
}