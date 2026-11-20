package me.capcom.smsgateway.services

import android.content.Context
import me.capcom.smsgateway.modules.gateway.workers.RegistrationWorker

/**
 * F-Droid flavor: no Firebase Cloud Messaging (proprietary Google Play
 * Services). Register with an empty push token — the server clears any
 * token left by a previous play-flavor install (null would mean "leave
 * unchanged" server-side) and then delivers commands over the SSE
 * transport, the same path the app already uses when the notification
 * channel is pinned to SSE_ONLY.
 */
object PushInterop {
    fun register(context: Context) {
        RegistrationWorker.start(context, "")
    }
}
