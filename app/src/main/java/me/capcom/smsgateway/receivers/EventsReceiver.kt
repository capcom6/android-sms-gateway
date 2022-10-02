package me.capcom.smsgateway.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import me.capcom.smsgateway.App
import me.capcom.smsgateway.data.entities.Message

class EventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // This method is called when the BroadcastReceiver is receiving an Intent broadcast.
        intent.action?.let { Log.d(this.javaClass.name, it) }
        intent.dataString?.let { Log.d(this.javaClass.name, it) }

        val state = when (intent.action) {
            ACTION_SENT -> when (resultCode) {
                Activity.RESULT_OK -> Message.State.Sent
                else -> Message.State.Failed
            }
            ACTION_DELIVERED -> Message.State.Delivered
            else -> return
        }
        val id = intent.dataString ?: return

        App.db.messageDao().updateState(id, state)
    }

    companion object {
        private var INSTANCE: EventsReceiver? = null

        const val ACTION_SENT = "me.capcom.smsgateway.ACTION_SENT"
        const val ACTION_DELIVERED = "me.capcom.smsgateway.ACTION_DELIVERED"

        fun getInstance(): EventsReceiver {
            return INSTANCE ?: EventsReceiver().also { INSTANCE = it }
        }

        fun register(context: Context) {
            context.registerReceiver(
                getInstance(),
                IntentFilter(ACTION_SENT)
                    .apply { addAction(ACTION_DELIVERED) }
            )
        }
    }
}