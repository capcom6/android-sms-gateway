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

    // Credentials of the live stream. A repeated connect() for the same account reuses
    // the running stream instead of opening a second one, while an account replacement
    // (different credentials) still takes effect.
    @Volatile
    private var connectedToken: String? = null

    // Marks the connection the event callbacks belong to. A connection is deactivated
    // before it is closed, so the failure OkHttp reports for a cancelled stream is
    // ignored instead of scheduling a reconnect that would kill the live stream.
    @Volatile
    private var connection: AtomicBoolean? = null

    private val lock = Any()

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

        synchronized(lock) {
            // Exactly one live stream: reuse the running one for the same credentials.
            // A second stream would deliver every cloud command twice.
            if (eventSource != null && connectedToken == token) return
        }

        scope.launch {
            val active = AtomicBoolean(true)
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
                            if (isDisconnecting.get() || !active.get()) return
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
                            if (isDisconnecting.get() || !active.get()) return
                            Log.d(TAG, "Event received: $type - $data")
                            onEvent?.invoke(type, data)
                        }

                        override fun onClosed(eventSource: EventSource) {
                            if (isDisconnecting.get() || !active.get()) return
                            Log.d(TAG, "SSE connection closed")
                            // The attempt is over: deactivate it, so an attempt that is
                            // still publishing drops the closed source instead of
                            // storing it as the live stream.
                            synchronized(lock) { active.set(false) }
                            onClosed?.invoke()
                            scheduleReconnect(active)
                        }

                        override fun onFailure(
                            eventSource: EventSource,
                            t: Throwable?,
                            response: Response?
                        ) {
                            if (isDisconnecting.get() || !active.get()) return
                            Log.e(TAG, "SSE error", t)
                            // The attempt is over: deactivate it, so an attempt that is
                            // still publishing drops the failed source instead of
                            // storing it as the live stream.
                            synchronized(lock) { active.set(false) }
                            onError?.invoke(t)
                            scheduleReconnect(active)
                        }
                    })

                synchronized(lock) {
                    // The attempt failed or closed before it could be published, or the
                    // credentials changed while it was in flight: it is stale, and
                    // storing it would block the reconnect with a dead stream.
                    if (!active.get() || tokenProvider() != token) {
                        source.cancel()
                        return@launch
                    }

                    // Close the previous stream before publishing the new one, so a
                    // repeated connect() can never leave two streams running.
                    connection?.set(false)
                    connection = active
                    eventSource?.cancel()
                    eventSource = source
                    connectedToken = token
                }

                // This body has no suspension point, so disconnect()'s
                // cancelChildren() cannot stop it once started: it may store the
                // stream after teardown already cleared the field. Store first,
                // then verify — paired with disconnect() setting the flag before
                // it grabs the field, one of the two always cancels the loser.
                if (isDisconnecting.get()) {
                    synchronized(lock) {
                        if (eventSource === source) {
                            eventSource = null
                            connection = null
                            connectedToken = null
                        }
                    }
                    active.set(false)
                    source.cancel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                scheduleReconnect(active)
            }
        }
    }

    fun disconnect() {
        isDisconnecting.set(true)
        // Kill delayed reconnects and in-flight launches FIRST: this teardown must
        // never be scheduled as a child, because cancelChildren() would cancel it
        // before it runs and leave the authenticated stream alive.
        scope.coroutineContext.cancelChildren()
        val source = synchronized(lock) {
            connection?.set(false)
            connection = null
            val current = eventSource
            eventSource = null
            connectedToken = null
            current
        }
        reconnectAttempts = 0
        source?.cancel()
    }

    private fun scheduleReconnect(active: AtomicBoolean) {
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
            synchronized(lock) {
                // Only tear down the connection this reconnect replaces: a newer
                // connection may already be live.
                if (connection === active) {
                    connection?.set(false)
                    connection = null
                    eventSource?.cancel()
                    eventSource = null
                    connectedToken = null
                }
            }
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