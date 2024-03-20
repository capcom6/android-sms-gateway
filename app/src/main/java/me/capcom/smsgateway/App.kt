package me.capcom.smsgateway

import android.app.Application
import me.capcom.smsgateway.data.dbModule
import me.capcom.smsgateway.modules.encryption.encryptionModule
import me.capcom.smsgateway.modules.gateway.GatewayModule
import me.capcom.smsgateway.modules.localserver.LocalServerModule
import me.capcom.smsgateway.modules.messages.messagesModule
import me.capcom.smsgateway.modules.notifications.notificationsModule
import me.capcom.smsgateway.modules.settings.PreferencesStorage
import me.capcom.smsgateway.modules.settings.settingsModule
import me.capcom.smsgateway.receivers.EventsReceiver
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class App: Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                settingsModule,
                dbModule,
                notificationsModule,
                messagesModule,
                encryptionModule,
                me.capcom.smsgateway.modules.gateway.gatewayModule,
            )
        }

        instance = this

        EventsReceiver.register(this)
    }

    val gatewayModule: GatewayModule by lazy {
        get()
    }

    val localServerModule by lazy {
        LocalServerModule(
            get(),
            PreferencesStorage(get(), "localserver")
        )
    }

    companion object {
        lateinit var instance: App
            private set
    }
}