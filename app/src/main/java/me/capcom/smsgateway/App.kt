package me.capcom.smsgateway

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.room.Room
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.GatewayModule
import me.capcom.smsgateway.modules.settings.PreferencesStorage
import me.capcom.smsgateway.receivers.EventsReceiver
import me.capcom.smsgateway.services.PushService

class App: Application() {
    override fun onCreate() {
        super.onCreate()

        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        db = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "gateway"
            )
            .allowMainThreadQueries()
            .build()
        events = EventBus()

        PushService.register(this)
        EventsReceiver.register(this)

        MainScope().launch {
            GatewayModule(this@App, PreferencesStorage(settings, "gateway"))
                .register(events)
        }
    }

    companion object {
        lateinit var db: AppDatabase
            private set
        lateinit var events: EventBus
            private set
    }
}