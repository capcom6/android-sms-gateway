package me.stappmus.messagegateway.modules.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

abstract class EventsReceiver : KoinComponent {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val eventBus = get<EventBus>()

    fun start() {
        stop()

        this.job = scope.launch {
            collect(eventBus)
        }
    }

    protected abstract suspend fun collect(eventBus: EventBus)

    fun stop() {
        this.job?.cancel()
        this.job = null
    }
}