package me.capcom.smsgateway.modules.encryption.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.capcom.smsgateway.modules.encryption.E2EKeyService
import me.capcom.smsgateway.modules.encryption.MODULE_NAME
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class KeyRotationWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {

    private val e2eKeySvc: E2EKeyService by inject()
    private val logsSvc: LogsService by inject()

    override suspend fun doWork(): Result = try {
        e2eKeySvc.rotateKeyIfDue()
        Result.success()
    } catch (e: Throwable) {
        logsSvc.insert(
            LogEntry.Priority.ERROR,
            MODULE_NAME,
            "Failed to rotate encryption key: ${e.message}"
        )
        e.printStackTrace()
        Result.retry()
    }

    companion object {
        private const val NAME = "KeyRotationWorker"

        fun start(context: Context) {
            val work = PeriodicWorkRequestBuilder<KeyRotationWorker>(1L, TimeUnit.DAYS)
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
