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
import kotlinx.coroutines.launch
import me.capcom.smsgateway.App
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.events.AppEvent

class PushService : FirebaseMessagingService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val settingsHelper by lazy { SettingsHelper(this) }

    override fun onNewToken(token: String) {
        settingsHelper.fcmToken = token

        scope.launch {
            App.events.emitEvent(AppEvent.FcmTokenUpdated(token))
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(this.javaClass.name, message.data.toString())
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
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
//                Log.d(this.javaClass.name, token)
            })
        }
    }
}