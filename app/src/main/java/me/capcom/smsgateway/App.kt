package me.capcom.smsgateway

import android.app.Application
import healthModule
import me.capcom.smsgateway.data.dbModule
import me.capcom.smsgateway.modules.encryption.encryptionModule
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.localserver.localserverService
import me.capcom.smsgateway.modules.messages.messagesModule
import me.capcom.smsgateway.modules.notifications.notificationsModule
import me.capcom.smsgateway.modules.ping.pingModule
import me.capcom.smsgateway.modules.settings.settingsModule
import me.capcom.smsgateway.modules.webhooks.webhooksModule
import me.capcom.smsgateway.receivers.EventsReceiver
import org.koin.android.ext.android.inject
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
                me.capcom.smsgateway.modules.gateway.gatewayService,
                healthModule,
                webhooksModule,
                localserverService,
                pingModule,
            )
        }

        instance = this

        EventsReceiver.register(this)
    }

    val gatewayService: GatewayService by inject()

    companion object {
        lateinit var instance: App
            private set
    }
}