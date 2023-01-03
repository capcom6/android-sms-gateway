package me.capcom.smsgateway.modules.gateway

import android.content.Context
import androidx.work.*
import me.capcom.smsgateway.App
import java.util.concurrent.TimeUnit

class RegistrationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val token = inputData.getString(DATA_TOKEN) ?: return Result.failure()

            App.instance.gatewayModule.registerFcmToken(token)

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        private const val NAME = "RegistrationWorker"
        private const val DATA_TOKEN = "token"

        fun start(context: Context, token: String) {
            val work = OneTimeWorkRequestBuilder<RegistrationWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(
                    DATA_TOKEN to token
                ))
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