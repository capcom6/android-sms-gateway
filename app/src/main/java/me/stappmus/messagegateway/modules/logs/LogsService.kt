package me.stappmus.messagegateway.modules.logs

import android.content.Context
import com.google.gson.GsonBuilder
import me.stappmus.messagegateway.extensions.configure
import me.stappmus.messagegateway.modules.logs.db.LogEntriesDao
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.logs.workers.TruncateWorker

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
        val fromTs = from ?: 0
        val toTs = to ?: System.currentTimeMillis()

        return dao.selectByPeriod(fromTs, toTs)
    }

    fun insert(
        priority: LogEntry.Priority,
        module: String,
        message: String,
        context: Any? = null
    ) {
        try {
            dao.insert(
                LogEntry(
                    priority,
                    module,
                    message,
                    context = context?.let { gson.toJsonTree(it) }
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun truncate() {
        val lifetimeDays = settings.lifetimeDays ?: return
        val until = System.currentTimeMillis() - lifetimeDays * 86400000L

        dao.truncate(until)
    }
}
