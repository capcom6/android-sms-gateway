package me.capcom.smsgateway.services

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.gateway.PullMessagesWorker
import me.capcom.smsgateway.modules.gateway.RegistrationWorker

class PushService : FirebaseMessagingService() {
    private val settingsHelper by lazy { SettingsHelper(this) }

    override fun onNewToken(token: String) {
        settingsHelper.fcmToken = token

        RegistrationWorker.start(this@PushService, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(this.javaClass.name, message.data.toString())
        PullMessagesWorker.start(this)
    }

    companion object {
        fun register(context: Context): Unit {
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(this.javaClass.name, "Fetching FCM registration token failed", task.exception)
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