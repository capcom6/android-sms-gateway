package me.capcom.smsgateway.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class EventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // This method is called when the BroadcastReceiver is receiving an Intent broadcast.
//        TODO("EventsReceiver.onReceive() is not implemented")
        intent.action?.let { Log.d(this.javaClass.name, it) }
    }

    companion object {
        const val ACTION_SENT = "me.capcom.smsgateway.ACTION_SENT"
        const val ACTION_DELIVERED = "me.capcom.smsgateway.ACTION_DELIVERED"
    }
}