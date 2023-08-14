package me.capcom.smsgateway.modules.localserver

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.localserver.events.IPReceivedEvent
import me.capcom.smsgateway.modules.messages.MessagesModule
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get
import me.capcom.smsgateway.providers.LocalIPProvider
import me.capcom.smsgateway.providers.PublicIPProvider
import me.capcom.smsgateway.services.WebService

class LocalServerModule(
    private val messagesModule: MessagesModule,
    private val storage: KeyValueStorage,
) {
    val events = EventBus()
    var enabled: Boolean
        get() = storage.get<Boolean>(ENABLED) ?: false
        set(value) = storage.set(ENABLED, value)

    fun start(context: Context) {
        if (!enabled) return
        WebService.start(context)

        scope.launch(Dispatchers.IO) {
            val localIP = LocalIPProvider(context).getIP() ?: "n/a"
            val remoteIP = PublicIPProvider().getIP() ?: "n/a"

            events.emitEvent(IPReceivedEvent(localIP, remoteIP))
        }
    }

    fun stop(context: Context) {
        WebService.stop(context)
    }

    companion object {
        private val job = SupervisorJob()
        private val scope = CoroutineScope(job)

        private const val ENABLED = "ENABLED"
    }
}