package me.capcom.smsgateway

import android.app.Application
import android.content.IntentFilter
import me.capcom.smsgateway.receivers.EventsReceiver

class App: Application() {
    override fun onCreate() {
        super.onCreate()

        registerReceiver(
            EventsReceiver.getInstance(),
            IntentFilter(EventsReceiver.ACTION_SENT)
                .apply { addAction(EventsReceiver.ACTION_DELIVERED) }
        )
    }
}