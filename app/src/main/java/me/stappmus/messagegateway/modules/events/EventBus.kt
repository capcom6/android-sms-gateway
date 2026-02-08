package me.stappmus.messagegateway.modules.events

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

class EventBus {
    private val _events = MutableSharedFlow<AppEvent>()
    val events = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) {
        Log.d("EventBus", "${Thread.currentThread().name} emitted ${event.name}")
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