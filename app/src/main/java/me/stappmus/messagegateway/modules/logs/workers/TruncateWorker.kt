package me.stappmus.messagegateway.modules.logs.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class TruncateWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {

    private val logsSvc: LogsService by inject()

    override suspend fun doWork(): Result = try {
        logsSvc.truncate()
        Result.success()
    } catch (e: Throwable) {
        logsSvc.insert(
            LogEntry.Priority.ERROR,
            me.stappmus.messagegateway.modules.logs.NAME,
            "Failed to truncate logs: ${e.message}"
        )
        e.printStackTrace()
        Result.retry()
    }

    companion object {
        private const val NAME = "TruncateWorker"

        fun start(context: Context) {
            val work = PeriodicWorkRequestBuilder<TruncateWorker>(1L, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    work
                )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(NAME)
        }
    }
}