package me.capcom.smsgateway.services

import android.content.Context
import me.capcom.smsgateway.modules.gateway.workers.RegistrationWorker

/**
 * F-Droid flavor: no Firebase Cloud Messaging (proprietary Google Play
 * Services). Register with a null push token so the cloud server delivers
 * commands over the SSE transport instead — same path the app already uses
 * when the notification channel is pinned to SSE_ONLY.
 */
object PushInterop {
    fun register(context: Context) {
        RegistrationWorker.start(context, null)
    }
}
