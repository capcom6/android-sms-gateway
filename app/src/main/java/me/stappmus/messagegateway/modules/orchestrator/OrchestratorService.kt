package me.stappmus.messagegateway.modules.orchestrator

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import android.os.PowerManager
import me.stappmus.messagegateway.helpers.SettingsHelper
import me.stappmus.messagegateway.modules.gateway.GatewayService
import me.stappmus.messagegateway.modules.localserver.LocalServerService
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.messages.MessagesService
import me.stappmus.messagegateway.modules.ping.PingService
import me.stappmus.messagegateway.modules.receiver.ReceiverService
import me.stappmus.messagegateway.modules.webhooks.WebHooksService

class OrchestratorService(
    private val messagesSvc: MessagesService,
    private val gatewaySvc: GatewayService,
    private val localServerSvc: LocalServerService,
    private val webHooksSvc: WebHooksService,
    private val receiverService: ReceiverService,
    private val pingSvc: PingService,
    private val logsSvc: LogsService,
    private val settings: SettingsHelper,
) {
    fun start(context: Context, autostart: Boolean) {
        if (autostart && !settings.autostart) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                logsSvc.insert(
                    LogEntry.Priority.WARN,
                    MODULE_NAME,
                    "Battery optimization is enabled. For reliable background delivery, disable optimization for this app."
                )
            }
        }

        logsSvc.start(context)
        messagesSvc.start(context)
        webHooksSvc.start(context)
        gatewaySvc.start(context)

        try {
            localServerSvc.start(context)
            pingSvc.start(context)
            receiverService.start(context)
        } catch (e: Throwable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                logsSvc.insert(
                    LogEntry.Priority.WARN,
                    MODULE_NAME,
                    "Can't start foreground services while the app is running in the background"
                )
                return
            }

            throw e
        }
    }

    fun stop(context: Context) {
        receiverService.stop(context)
        pingSvc.stop(context)
        localServerSvc.stop(context)

        gatewaySvc.stop(context)
        webHooksSvc.stop(context)
        messagesSvc.stop(context)
        logsSvc.stop(context)
    }
}
