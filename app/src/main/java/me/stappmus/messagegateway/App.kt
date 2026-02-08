package me.stappmus.messagegateway

import android.app.Application
import healthModule
import me.stappmus.messagegateway.data.dbModule
import me.stappmus.messagegateway.modules.connection.connectionModule
import me.stappmus.messagegateway.modules.encryption.encryptionModule
import me.stappmus.messagegateway.modules.events.eventBusModule
import me.stappmus.messagegateway.modules.gateway.GatewayService
import me.stappmus.messagegateway.modules.localserver.localserverModule
import me.stappmus.messagegateway.modules.logs.logsModule
import me.stappmus.messagegateway.modules.media.mediaModule
import me.stappmus.messagegateway.modules.messages.messagesModule
import me.stappmus.messagegateway.modules.notifications.notificationsModule
import me.stappmus.messagegateway.modules.orchestrator.OrchestratorService
import me.stappmus.messagegateway.modules.orchestrator.orchestratorModule
import me.stappmus.messagegateway.modules.ping.pingModule
import me.stappmus.messagegateway.modules.receiver.receiverModule
import me.stappmus.messagegateway.modules.settings.settingsModule
import me.stappmus.messagegateway.modules.webhooks.webhooksModule
import me.stappmus.messagegateway.receivers.EventsReceiver
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
                me.stappmus.messagegateway.modules.gateway.gatewayModule,
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
