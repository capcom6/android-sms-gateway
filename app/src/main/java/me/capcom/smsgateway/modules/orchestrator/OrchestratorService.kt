package me.capcom.smsgateway.modules.orchestrator

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.localserver.LocalServerService
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.ping.PingService
import me.capcom.smsgateway.modules.webhooks.WebHooksService

class OrchestratorService(
    private val messagesSvc: MessagesService,
    private val gatewaySvc: GatewayService,
    private val localServerSvc: LocalServerService,
    private val webHooksSvc: WebHooksService,
    private val pingSvc: PingService,
    private val logsSvc: LogsService,
) {
    fun start(context: Context) {
        logsSvc.start(context)
        messagesSvc.start(context)
        gatewaySvc.start(context)
        webHooksSvc.start(context)

        try {
            localServerSvc.start(context)
            pingSvc.start(context)
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

            throw e;
        }
    }

    fun stop(context: Context) {
        pingSvc.stop(context)
        webHooksSvc.stop(context)
        localServerSvc.stop(context)
        gatewaySvc.stop(context)
        messagesSvc.stop(context)
        logsSvc.stop(context)
    }
}