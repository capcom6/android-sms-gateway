package me.capcom.smsgateway

import android.app.Application
import healthModule
import me.capcom.smsgateway.data.dbModule
import me.capcom.smsgateway.modules.connection.connectionModule
import me.capcom.smsgateway.modules.encryption.encryptionModule
import me.capcom.smsgateway.modules.events.eventBusModule
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.localserver.localserverModule
import me.capcom.smsgateway.modules.logs.logsModule
import me.capcom.smsgateway.modules.media.mediaModule
import me.capcom.smsgateway.modules.messages.messagesModule
import me.capcom.smsgateway.modules.notifications.notificationsModule
import me.capcom.smsgateway.modules.orchestrator.OrchestratorService
import me.capcom.smsgateway.modules.orchestrator.orchestratorModule
import me.capcom.smsgateway.modules.ping.pingModule
import me.capcom.smsgateway.modules.receiver.receiverModule
import me.capcom.smsgateway.modules.settings.settingsModule
import me.capcom.smsgateway.modules.webhooks.webhooksModule
import me.capcom.smsgateway.receivers.EventsReceiver
import org.koin.android.ext.android.get
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
                eventBusModule,
                settingsModule,
                dbModule,
                logsModule,
                notificationsModule,
                messagesModule,
                mediaModule,
                receiverModule,
                encryptionModule,
                me.capcom.smsgateway.modules.gateway.gatewayModule,
                healthModule,
                webhooksModule,
                localserverModule,
                pingModule,
                connectionModule,
                orchestratorModule,
            )
        }

        Thread.setDefaultUncaughtExceptionHandler(
            GlobalExceptionHandler(
                Thread.getDefaultUncaughtExceptionHandler()!!,
                get()
            )
        )

        instance = this

        EventsReceiver.register(this)

        get<OrchestratorService>().start(this, true)
    }

    val gatewayService: GatewayService by inject()

    companion object {
        lateinit var instance: App
            private set
    }
}
