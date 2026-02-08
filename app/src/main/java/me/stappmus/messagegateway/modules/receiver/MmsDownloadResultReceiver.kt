package me.stappmus.messagegateway.modules.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import com.klinker.android.send_message.MmsReceivedReceiver
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MmsDownloadResultReceiver : BroadcastReceiver(), KoinComponent {
    private val logsService: LogsService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID)

        if (resultCode == Activity.RESULT_OK) {
            val forwardIntent = Intent(MmsReceivedReceiver.MMS_RECEIVED).apply {
                setPackage(context.packageName)
                putExtras(intent.extras ?: return@apply)
            }

            context.sendBroadcast(forwardIntent)

            logsService.insert(
                priority = LogEntry.Priority.DEBUG,
                module = MODULE_NAME,
                message = "MMS download callback success",
                context = mapOf(
                    "transactionId" to transactionId,
                )
            )
            return
        }

        logsService.insert(
            priority = LogEntry.Priority.WARN,
            module = MODULE_NAME,
            message = "MMS download callback failed",
            context = mapOf(
                "transactionId" to transactionId,
                "resultCode" to resultCode,
            )
        )

        Log.w(TAG, "MMS download callback failed tx=$transactionId resultCode=$resultCode")
    }

    companion object {
        private const val TAG = "MmsDownloadResult"
        private const val MODULE_NAME = "receiver"
        private const val EXTRA_TRANSACTION_ID = "transaction_id"
    }
}
