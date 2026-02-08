package me.stappmus.messagegateway.services

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import me.stappmus.messagegateway.modules.events.ExternalEvent
import me.stappmus.messagegateway.modules.events.ExternalEventType
import me.stappmus.messagegateway.modules.gateway.workers.RegistrationWorker
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.orchestrator.EventsRouter
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

class PushService : FirebaseMessagingService(), KoinComponent {
    private val logsService by inject<LogsService>()
    private val eventsRouter by inject<EventsRouter>()

    override fun onNewToken(token: String) {
        RegistrationWorker.start(this@PushService, token, true)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            Log.d(this.javaClass.name, message.data.toString())

            val event = message.data["event"]?.let { ExternalEventType.valueOf(it) }
                ?: ExternalEventType.MessageEnqueued
            val data = message.data["data"]

            Log.d(this.javaClass.name, "Routing event: $event with data: $data")

            eventsRouter.route(
                ExternalEvent(
                    type = event,
                    data = data,
                )
            )
        } catch (e: Throwable) {
            Log.e(this.javaClass.name, "Error processing push message", e)
            logsService.insert(
                priority = LogEntry.Priority.ERROR,
                module = this.javaClass.simpleName,
                message = "Failed to process push message: ${e.message}",
                mapOf("error" to e.toString())
            )
        }
    }

    companion object : KoinComponent {
        fun register(context: Context): Unit {
            val logger = get<LogsService>()

            logger.insert(
                priority = LogEntry.Priority.INFO,
                module = PushService::class.java.simpleName,
                message = "FCM registration started"
            )
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful || task.isCanceled) {
                    logger.insert(
                        priority = LogEntry.Priority.ERROR,
                        module = PushService::class.java.simpleName,
                        message = "Fetching FCM registration token failed: ${task.exception}"
                    )
                }

                // Get new FCM registration token
                val token = try {
                    task.result
                } catch (e: Throwable) {
                    null
                }

                logger.insert(
                    priority = LogEntry.Priority.INFO,
                    module = PushService::class.java.simpleName,
                    message = "FCM registration finished",
                    mapOf("token" to token)
                )

                // Log and toast
                RegistrationWorker.start(context, token, false)
            }
        }
    }
}