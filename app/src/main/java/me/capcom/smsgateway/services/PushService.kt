package me.capcom.smsgateway.services

import android.content.Context
import android.util.Log
import android.widget.Toast
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
import me.capcom.smsgateway.modules.gateway.workers.WebhooksUpdateWorker
import me.capcom.smsgateway.modules.push.Event
import me.capcom.smsgateway.modules.push.events.PushMessageEnqueuedEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PushService : FirebaseMessagingService(), KoinComponent {
    private val settingsHelper by lazy { SettingsHelper(this) }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventBus by inject<EventBus>()

    override fun onNewToken(token: String) {
        settingsHelper.fcmToken = token

        RegistrationWorker.start(this@PushService, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            Log.d(this.javaClass.name, message.data.toString())

            val event = message.data["event"]?.let { Event.valueOf(it) } ?: Event.MessageEnqueued
            when (event) {
                Event.MessageEnqueued -> scope.launch { eventBus.emit(PushMessageEnqueuedEvent()) }
                Event.WebhooksUpdated -> WebhooksUpdateWorker.start(this)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun register(context: Context): Unit {
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(
                        context,
                        "Fetching FCM registration token failed: ${task.exception}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.w(
                        this::class.java.name,
                        "Fetching FCM registration token failed",
                        task.exception
                    )
                    return@OnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result

                // Log and toast
                RegistrationWorker.start(context, token)
            })
        }
    }
}