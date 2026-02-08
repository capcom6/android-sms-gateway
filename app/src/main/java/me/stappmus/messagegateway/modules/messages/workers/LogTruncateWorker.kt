package me.stappmus.messagegateway.modules.messages.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.stappmus.messagegateway.modules.messages.MessagesService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class LogTruncateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(
    appContext,
    params
), KoinComponent {
    private val messagesSvc: MessagesService by inject()

    override suspend fun doWork(): Result = try {
        messagesSvc.truncateLog()
        Result.success()
    } catch (e: Throwable) {
        e.printStackTrace()
        Result.retry()
    }

    companion object {
        private const val NAME = "LogTruncateWorker"

        fun start(context: Context) {
            val work = PeriodicWorkRequestBuilder<LogTruncateWorker>(1L, TimeUnit.DAYS)
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