package me.capcom.smsgateway.modules.incoming.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import me.capcom.smsgateway.modules.incoming.IncomingMessagesService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class IncomingLogTruncateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(
    appContext,
    params
), KoinComponent {
    private val incomingSvc: IncomingMessagesService by inject()

    override suspend fun doWork(): Result = try {
        incomingSvc.truncateLog()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        e.printStackTrace()
        Result.retry()
    }

    companion object {
        private const val NAME = "IncomingLogTruncateWorker"

        fun start(context: Context) {
            val work = PeriodicWorkRequestBuilder<IncomingLogTruncateWorker>(1L, TimeUnit.DAYS)
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
