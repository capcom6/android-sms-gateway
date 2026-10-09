package me.capcom.smsgateway.helpers

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
    private val tokenProvider: () -> String?,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(1, TimeUnit.HOURS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
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
        val token = tokenProvider() ?: return disconnect()

        scope.launch {
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        header("Authorization", "Bearer $token")
                    }
                    .build()

                val source = EventSources.createFactory(client)
                    .newEventSource(request, object : EventSourceListener() {
                        override fun onOpen(eventSource: EventSource, response: Response) {
                            if (isDisconnecting.get()) return
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
                            if (isDisconnecting.get()) return
                            Log.d(TAG, "Event received: $type - $data")
                            onEvent?.invoke(type, data)
                        }

                        override fun onClosed(eventSource: EventSource) {
                            if (isDisconnecting.get()) return
                            Log.d(TAG, "SSE connection closed")
                            onClosed?.invoke()
                            scheduleReconnect()
                        }

                        override fun onFailure(
                            eventSource: EventSource,
                            t: Throwable?,
                            response: Response?
                        ) {
                            if (isDisconnecting.get()) return
                            Log.e(TAG, "SSE error", t)
                            onError?.invoke(t)
                            scheduleReconnect()
                        }
                    })

                // This body has no suspension point, so disconnect()'s
                // cancelChildren() cannot stop it once started: it may store the
                // stream after teardown already cleared the field. Store first,
                // then verify — paired with disconnect() setting the flag before
                // it grabs the field, one of the two always cancels the loser.
                eventSource = source
                if (isDisconnecting.get()) {
                    eventSource = null
                    source.cancel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        isDisconnecting.set(true)
        // Kill delayed reconnects and in-flight launches FIRST: this teardown must
        // never be scheduled as a child, because cancelChildren() would cancel it
        // before it runs and leave the authenticated stream alive.
        scope.coroutineContext.cancelChildren()
        val source = eventSource
        eventSource = null
        reconnectAttempts = 0
        source?.cancel()
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
            if (isDisconnecting.get()) return@launch
            Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")
            kotlinx.coroutines.delay(delay)
            if (isDisconnecting.get()) return@launch
            connect()
        }
    }

    companion object {
        const val TAG = "SSEManager"
    }
}