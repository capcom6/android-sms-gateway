package me.capcom.smsgateway.services

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import me.capcom.smsgateway.modules.events.ExternalEvent
import me.capcom.smsgateway.modules.events.ExternalEventType
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.gateway.PUSH_TOKEN_TYPE_FID
import me.capcom.smsgateway.modules.gateway.workers.RegistrationWorker
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.orchestrator.EventsRouter
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

class PushService : FirebaseMessagingService(), KoinComponent {
    private val logsService by inject<LogsService>()
    private val eventsRouter by inject<EventsRouter>()
    private val gatewaySettings by inject<GatewaySettings>()

    override fun onRegistered(installationId: String) {
        if (gatewaySettings.notificationChannel == GatewaySettings.NotificationChannel.SSE_ONLY) {
            Log.d(this.javaClass.name, "FID registration ignored: SSE_ONLY channel configured")
            RegistrationWorker.start(this@PushService, null)
            return
        }

        Log.d(this.javaClass.name, "FID registration received")
        logsService.insert(
            priority = LogEntry.Priority.INFO,
            module = this.javaClass.simpleName,
            message = "FID registration received",
        )
        RegistrationWorker.start(this@PushService, installationId, PUSH_TOKEN_TYPE_FID)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            Log.d(this.javaClass.name, message.data.toString())

            // The server keeps the push token after unregister, so it may still deliver
            // commands for the previous registration. Reject them here, at the entry
            // point, rather than letting each consumer re-check the registration.
            if (gatewaySettings.registrationInfo == null) {
                Log.d(this.javaClass.name, "Push message ignored: device is not registered")
                logsService.insert(
                    priority = LogEntry.Priority.INFO,
                    module = this.javaClass.simpleName,
                    message = "Push message ignored: device is not registered"
                )
                return
            }

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
            val settings = get<GatewaySettings>()

            if (settings.notificationChannel == GatewaySettings.NotificationChannel.SSE_ONLY) {
                logger.insert(
                    priority = LogEntry.Priority.INFO,
                    module = PushService::class.java.simpleName,
                    message = "FCM registration skipped: SSE_ONLY channel configured"
                )
                RegistrationWorker.start(context, null)
                return
            }


            logger.insert(
                priority = LogEntry.Priority.INFO,
                module = PushService::class.java.simpleName,
                message = "FCM registration started"
            )
            FirebaseMessaging.getInstance().register()
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful || task.isCanceled) {
                        logger.insert(
                            priority = LogEntry.Priority.ERROR,
                            module = PushService::class.java.simpleName,
                            message = "FCM registration failed: ${task.exception}"
                        )
                        RegistrationWorker.start(context, null, null)
                        return@addOnCompleteListener
                    }

                    logger.insert(
                        priority = LogEntry.Priority.INFO,
                        module = PushService::class.java.simpleName,
                        message = "FCM registration finished; awaiting onRegistered callback"
                    )
                }
        }
    }
}