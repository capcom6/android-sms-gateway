package me.capcom.smsgateway.modules.gateway.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import me.capcom.smsgateway.App
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class RegistrationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {
    private val logsSvc: LogsService by inject()

    override suspend fun doWork(): Result {
        try {
            val token = inputData.getString(DATA_TOKEN)

            App.instance.gatewayService.registerFcmToken(token)

            return Result.success()
        } catch (e: Exception) {
            logsSvc.insert(
                priority = LogEntry.Priority.ERROR,
                module = NAME,
                message = "Registration failed: ${e.message}",
                context = mapOf(
                    "token" to inputData.getString(DATA_TOKEN)
                )
            )
            e.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        private const val NAME = "RegistrationWorker"
        private const val DATA_TOKEN = "token"

        fun start(context: Context, token: String?) {
            val work = OneTimeWorkRequestBuilder<RegistrationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setInputData(
                    workDataOf(
                        DATA_TOKEN to token
                    )
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    NAME,
                    ExistingWorkPolicy.REPLACE,
                    work
                )
        }
    }
}