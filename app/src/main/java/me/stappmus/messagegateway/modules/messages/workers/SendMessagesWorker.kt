package me.stappmus.messagegateway.modules.messages.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import me.stappmus.messagegateway.R
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.messages.MessagesService
import me.stappmus.messagegateway.modules.notifications.NotificationsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class SendMessagesWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {

    private val logsService: LogsService by inject()
    private val messagesSvc: MessagesService by inject()
    private val notificationsSvc: NotificationsService by inject()

    override suspend fun doWork(): Result {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            NAME,
            "SendMessagesWorker started"
        )

        return try {
            messagesSvc.sendPendingMessages()

            logsService.insert(
                LogEntry.Priority.DEBUG,
                NAME,
                "SendMessagesWorker finished successfully"
            )

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()

            logsService.insert(
                LogEntry.Priority.ERROR,
                NAME,
                "SendMessagesWorker finished with error: ${e.message ?: e.toString()}"
            )

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
            notificationId,
            applicationContext.getString(R.string.send_messages_notification)
        )

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        private const val NAME = "SendMessagesWorker"

        fun start(context: Context, force: Boolean) {
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
                    when (force) {
                        true -> ExistingWorkPolicy.REPLACE
                        false -> ExistingWorkPolicy.KEEP
                    },
                    work
                )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(NAME)
        }
    }
}