package me.capcom.smsgateway.services

import android.content.Context

/**
 * Play flavor: registers the device with the cloud server through Firebase
 * Cloud Messaging.
 */
object PushInterop {
    fun register(context: Context) {
        PushService.register(context)
    }
}
