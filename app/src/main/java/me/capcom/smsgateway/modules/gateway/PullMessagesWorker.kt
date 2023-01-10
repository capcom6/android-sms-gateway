package me.capcom.smsgateway.modules.gateway

import android.content.Context
import androidx.work.*
import me.capcom.smsgateway.App
import java.util.concurrent.TimeUnit

class PullMessagesWorker(appContext: Context,
                         params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        try {
            App.instance.gatewayModule.getNewMessages()

            return Result.success()
        } catch (th: Throwable) {
            return Result.failure()
        }
    }

    companion object {
        const val NAME = "PullMessagesWorker"

        fun start(context: Context) {
            val work = PeriodicWorkRequestBuilder<PullMessagesWorker>(60L, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    work
                )
        }
    }
}