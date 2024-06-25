package me.capcom.smsgateway.modules.orchestrator

import android.content.Context
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.localserver.LocalServerService
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.ping.PingService

class OrchestratorService(
    private val messagesSvc: MessagesService,
    private val gatewaySvc: GatewayService,
    private val localServerSvc: LocalServerService,
    private val pingSvc: PingService
) {
    fun start(context: Context) {
        messagesSvc.start(context)
        gatewaySvc.start(context)
        localServerSvc.start(context)
        pingSvc.start(context)
    }

    fun ping(context: Context) {
        gatewaySvc.ping(context)
    }

    fun stop(context: Context) {
        pingSvc.stop(context)
        localServerSvc.stop(context)
        gatewaySvc.stop(context)
        messagesSvc.stop(context)
    }
}