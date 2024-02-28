package me.capcom.smsgateway.modules.messages.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.notifications.NotificationsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class SendMessagesWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {

    private val messagesSvc: MessagesService by inject()
    private val notificationsSvc: NotificationsService by inject()

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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun createForegroundInfo(): ForegroundInfo {
        val notificationId = NotificationsService.NOTIFICATION_ID_SEND_WORKER
        val notification = notificationsSvc.makeNotification(
            applicationContext,
            applicationContext.getString(R.string.send_messages_notification)
        )

        return ForegroundInfo(notificationId, notification)
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

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(NAME)
        }
    }
}