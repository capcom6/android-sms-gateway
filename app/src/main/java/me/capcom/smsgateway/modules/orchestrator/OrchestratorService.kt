package me.capcom.smsgateway.modules.orchestrator

import android.content.Context
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.localserver.LocalServerService
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.ping.PingService
import me.capcom.smsgateway.modules.webhooks.WebHooksService

class OrchestratorService(
    private val messagesSvc: MessagesService,
    private val gatewaySvc: GatewayService,
    private val localServerSvc: LocalServerService,
    private val webHooksSvc: WebHooksService,
    private val pingSvc: PingService
) {
    fun start(context: Context) {
        messagesSvc.start(context)
        gatewaySvc.start(context)
        localServerSvc.start(context)
        webHooksSvc.start(context)
        pingSvc.start(context)
    }

    fun stop(context: Context) {
        pingSvc.stop(context)
        webHooksSvc.stop(context)
        localServerSvc.stop(context)
        gatewaySvc.stop(context)
        messagesSvc.stop(context)
    }
}