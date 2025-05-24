package me.capcom.smsgateway.services

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.workers.RegistrationWorker
import me.capcom.smsgateway.modules.gateway.workers.SettingsUpdateWorker
import me.capcom.smsgateway.modules.gateway.workers.WebhooksUpdateWorker
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.push.Event
import me.capcom.smsgateway.modules.push.events.PushMessageEnqueuedEvent
import me.capcom.smsgateway.modules.push.payloads.MessagesExportRequestedPayload
import me.capcom.smsgateway.modules.receiver.ReceiverService
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

class PushService : FirebaseMessagingService(), KoinComponent {
    private val settingsHelper by inject<SettingsHelper>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventBus by inject<EventBus>()

    override fun onNewToken(token: String) {
        settingsHelper.fcmToken = token

        RegistrationWorker.start(this@PushService, token, true)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            Log.d(this.javaClass.name, message.data.toString())

            val event = message.data["event"]?.let { Event.valueOf(it) } ?: Event.MessageEnqueued
            val data = message.data["data"]
            when (event) {
                Event.MessageEnqueued -> scope.launch { eventBus.emit(PushMessageEnqueuedEvent()) }
                Event.WebhooksUpdated -> WebhooksUpdateWorker.start(this)
                Event.MessagesExportRequested -> data
                    ?.let {
                        MessagesExportRequestedPayload.from(
                            data
                        )
                    }
                    ?.let { payload ->
                        get<ReceiverService>().export(
                            this,
                            payload.since to payload.until
                        )
                    }

                Event.SettingsUpdated -> SettingsUpdateWorker.start(this)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object : KoinComponent {
        fun register(context: Context): Unit {
            val logger = get<LogsService>()

            logger.insert(
                priority = LogEntry.Priority.INFO,
                module = PushService::class.java.simpleName,
                message = "FCM registration started"
            )
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
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
            })
        }
    }
}