package me.capcom.smsgateway.modules.messages.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import me.capcom.smsgateway.modules.messages.MessagesService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class SendMessagesWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {

    private val messagesSvc: MessagesService by inject()

    override suspend fun doWork(): Result {
        return try {
            while (messagesSvc.sendPendingMessages()) {
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()

            Result.retry()
        }
    }

    companion object {
        private const val NAME = "SendMessagesWorker"

        fun start(context: Context) {
            val work = OneTimeWorkRequestBuilder<SendMessagesWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    NAME,
                    ExistingWorkPolicy.KEEP,
                    work
                )
        }
    }
}