package me.capcom.smsgateway

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.room.Room
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.modules.gateway.GatewayModule
import me.capcom.smsgateway.modules.settings.PreferencesStorage
import me.capcom.smsgateway.receivers.EventsReceiver
import me.capcom.smsgateway.services.PushService

class App: Application() {
    override fun onCreate() {
        super.onCreate()

        instance = this

        PushService.register(this)
        EventsReceiver.register(this)
    }

    val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "gateway"
        )
        .allowMainThreadQueries()
        .build()
    }

    val settings by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    val gatewayModule by lazy { GatewayModule(this, PreferencesStorage(settings, "gateway")) }

    companion object {
        lateinit var instance: App
            private set
    }
}