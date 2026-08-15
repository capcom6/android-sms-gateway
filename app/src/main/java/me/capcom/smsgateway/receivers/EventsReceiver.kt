package me.capcom.smsgateway.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.LogsUtils.toLogContext
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.messages.MODULE_NAME
import me.capcom.smsgateway.modules.messages.MessagesService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EventsReceiver : BroadcastReceiver(), KoinComponent {

    private val messagesService: MessagesService by inject()
    private val logsService: LogsService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val capturedResultCode = pendingResult.resultCode

        scope.launch {
            try {
                messagesService.processStateIntent(intent, capturedResultCode)
            } catch (e: Throwable) {
                logsService.insert(
                    LogEntry.Priority.ERROR,
                    MODULE_NAME,
                    "Can't process message state intent",
                    intent.toLogContext() + e.toLogContext()
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val job = SupervisorJob()
        private val scope = CoroutineScope(job)

        private var INSTANCE: EventsReceiver? = null

        const val ACTION_SENT = "me.capcom.smsgateway.ACTION_SENT"
        const val ACTION_DELIVERED = "me.capcom.smsgateway.ACTION_DELIVERED"
        const val ACTION_MMS_SENT = "me.capcom.smsgateway.ACTION_MMS_SENT"

        const val EXTRA_MESSAGE_ID = "messageId"
        const val EXTRA_PDU_PATH = "pduPath"
        
        private fun getInstance(): EventsReceiver {
            return INSTANCE ?: EventsReceiver().also { INSTANCE = it }
        }

        fun register(context: Context) {
            context.registerReceiver(
                getInstance(),
                IntentFilter(ACTION_SENT).apply {
                    addAction(ACTION_DELIVERED)
                    addAction(ACTION_MMS_SENT)
                }
            )
        }
    }
}