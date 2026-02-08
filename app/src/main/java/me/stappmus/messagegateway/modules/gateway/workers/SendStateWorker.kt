package me.stappmus.messagegateway.modules.gateway.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.stappmus.messagegateway.modules.gateway.GatewayService
import me.stappmus.messagegateway.modules.messages.MessagesService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class SendStateWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {
    private val messagesService: MessagesService by inject()
    private val gatewayService: GatewayService by inject()
    override suspend fun doWork(): Result {
        try {
            val messageId = inputData.getString(MESSAGE_ID) ?: return Result.failure()
            val message = messagesService.getMessage(messageId) ?: return Result.failure()

            withContext(Dispatchers.IO) {
                gatewayService.sendState(message)
            }
            return Result.success()
        } catch (th: Throwable) {
            th.printStackTrace()
            return when {
                this.runAttemptCount < RETRY_COUNT -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    companion object {
        private const val RETRY_COUNT = 10

        private const val MESSAGE_ID = "messageId"

        fun start(context: Context, messageId: String) {
            val work = OneTimeWorkRequestBuilder<SendStateWorker>()
                .setInputData(workDataOf(MESSAGE_ID to messageId))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueue(work)
        }
    }
}