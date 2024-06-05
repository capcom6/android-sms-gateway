package me.capcom.smsgateway.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.capcom.smsgateway.App
import me.capcom.smsgateway.modules.localserver.LocalServerService
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class BootReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        App.instance.gatewayModule.start(context)
        get<LocalServerService>().start(context)
    }
}