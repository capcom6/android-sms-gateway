package me.stappmus.messagegateway.modules.ping

import android.content.Context
import me.stappmus.messagegateway.modules.ping.services.PingForegroundService

class PingService(
    private val settings: PingSettings,
) {
    fun start(context: Context) {
        if (!settings.enabled) return
        PingForegroundService.start(context)
    }

    fun stop(context: Context) {
        PingForegroundService.stop(context)
    }
}