package me.stappmus.messagegateway.helpers

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SSEManager(
    private val url: String,
    private val authToken: String
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(1, TimeUnit.HOURS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var eventSource: EventSource? = null
    private var reconnectAttempts = 0
    private val isDisconnecting = AtomicBoolean(false)

    // Event callbacks
    var onEvent: ((type: String?, data: String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onError: ((Throwable?) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    fun connect() {
        isDisconnecting.set(false)
        scope.launch {
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        header("Authorization", "Bearer $authToken")
                    }
                    .build()

                eventSource = EventSources.createFactory(client)
                    .newEventSource(request, object : EventSourceListener() {
                        override fun onOpen(eventSource: EventSource, response: Response) {
                            Log.d(TAG, "SSE connected")
                            reconnectAttempts = 0
                            onConnected?.invoke()
                        }

                        override fun onEvent(
                            eventSource: EventSource,
                            id: String?,
                            type: String?,
                            data: String
                        ) {
                            Log.d(TAG, "Event received: $type - $data")
                            onEvent?.invoke(type, data)
                        }

                        override fun onClosed(eventSource: EventSource) {
                            Log.d(TAG, "SSE connection closed")
                            onClosed?.invoke()
                            scheduleReconnect()
                        }

                        override fun onFailure(
                            eventSource: EventSource,
                            t: Throwable?,
                            response: Response?
                        ) {
                            Log.e(TAG, "SSE error", t)
                            onError?.invoke(t)
                            scheduleReconnect()
                        }
                    })
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        isDisconnecting.set(true)
        scope.launch {
            eventSource?.cancel()
            eventSource = null
            reconnectAttempts = 0
        }
        scope.coroutineContext.cancelChildren()
    }

    private fun scheduleReconnect() {
        if (isDisconnecting.get()) {
            return
        }

        reconnectAttempts++
        val delay = when {
            reconnectAttempts > 10 -> 60_000L  // 1 minute
            reconnectAttempts > 5 -> 30_000L   // 30 seconds
            else -> 5_000L                     // 5 seconds
        }

        scope.launch {
            eventSource?.cancel()
            eventSource = null
            Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")
            kotlinx.coroutines.delay(delay)
            connect()
        }
    }

    companion object {
        const val TAG = "SSEManager"
    }
}