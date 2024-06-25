package me.capcom.smsgateway.modules.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

class EventBus {
    private val _events = MutableSharedFlow<AppEvent>()
    val events = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    suspend inline fun <reified T : AppEvent> collect(crossinline block: suspend (T) -> Unit) {
        events
            .filter {
                it is T
            }
            .collect { block(it as T) }
    }
}