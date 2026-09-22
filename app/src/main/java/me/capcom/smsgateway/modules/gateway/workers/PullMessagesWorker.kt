package me.capcom.smsgateway.modules.gateway.workers

import android.content.Context
import androidx.lifecycle.map
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.App
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.notifications.NotificationsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class PullMessagesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {
    private val notificationsSvc: NotificationsService by inject()

    override suspend fun doWork(): Result {
        try {
            withContext(Dispatchers.IO) {
                App.instance.gatewayService.getNewMessages(
                    applicationContext
                )
            }
            return Result.success()
        } catch (th: Throwable) {
            th.printStackTrace()
            return Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    // Expedited work runs as a foreground service below API 31.
    private fun createForegroundInfo(): ForegroundInfo {
        val notificationId = NotificationsService.NOTIFICATION_ID_PULL_WORKER
        val notification = notificationsSvc.makeNotification(
            applicationContext,
            notificationId,
            applicationContext.getString(R.string.pull_messages_notification)
        )

        return ForegroundInfo(notificationId, notification)
    }

    companion object {
        const val NAME = "PullMessagesWorker"

        // Unique periodic and one-time work share a namespace, so the one-shot needs its own name.
        private const val NAME_ONCE = "PullMessagesWorker:once"

        fun start(context: Context) {
            val work = PeriodicWorkRequestBuilder<PullMessagesWorker>(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
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

        // Expedited so the pull lands in the push's wake window, not the next
        // Doze maintenance window. REPLACE so a trigger arriving mid-pull still
        // gets a fetch starting after it.
        fun startOnce(context: Context) {
            val work = OneTimeWorkRequestBuilder<PullMessagesWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    NAME_ONCE,
                    ExistingWorkPolicy.REPLACE,
                    work
                )
        }

        fun getStateLiveData(context: Context) = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(NAME)
            WorkManager.getInstance(context)
                .cancelUniqueWork(NAME_ONCE)
        }
    }
}
