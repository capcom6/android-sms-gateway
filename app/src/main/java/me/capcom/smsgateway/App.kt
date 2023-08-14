package me.capcom.smsgateway

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.room.Room
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.modules.gateway.GatewayModule
import me.capcom.smsgateway.modules.localserver.LocalServerModule
import me.capcom.smsgateway.modules.messages.MessagesModule
import me.capcom.smsgateway.modules.settings.PreferencesStorage
import me.capcom.smsgateway.receivers.EventsReceiver

class App: Application() {
    override fun onCreate() {
        super.onCreate()

        instance = this

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
    val messagesModule by lazy { MessagesModule(this, db.messageDao()) }
    val gatewayModule by lazy { GatewayModule(messagesModule, PreferencesStorage(settings, "gateway")) }
    val localServerModule by lazy { LocalServerModule(messagesModule, PreferencesStorage(settings, "localserver")) }

    companion object {
        lateinit var instance: App
            private set
    }
}