package me.capcom.smsgateway.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.capcom.smsgateway.services.WebService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WebService.start(context)
    }
}