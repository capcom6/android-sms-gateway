package me.capcom.smsgateway.modules.logs

import android.content.Context
import com.google.gson.GsonBuilder
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.logs.db.LogEntriesDao
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.logs.workers.TruncateWorker

class LogsService(
    private val dao: LogEntriesDao,
    private val settings: LogsSettings,
) {
    private val gson = GsonBuilder().configure().create()

    fun start(context: Context) {
        TruncateWorker.start(context)
    }

    fun stop(context: Context) {
        TruncateWorker.stop(context)
    }

    suspend fun select(
        from: Long? = null,
        to: Long? = null
    ): List<LogEntry> {
        val from = from ?: 0
        val to = to ?: System.currentTimeMillis()

        return dao.selectByPeriod(from, to)
    }

    suspend fun insert(
        priority: LogEntry.Priority,
        module: String,
        message: String,
        context: Any? = null
    ) {
        dao.insert(
            LogEntry(
                priority,
                module,
                message,
                context = context?.let { gson.toJsonTree(it) }
            )
        )
    }

    suspend fun truncate() {
        val lifetimeDays = settings.lifetimeDays ?: return
        val until = System.currentTimeMillis() - lifetimeDays * 86400000L

        dao.truncate(until)
    }
}