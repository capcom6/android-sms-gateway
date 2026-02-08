package me.stappmus.messagegateway.modules.receiver.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.media.MediaService
import me.stappmus.messagegateway.modules.receiver.ReceiverService
import me.stappmus.messagegateway.modules.receiver.data.InboxMessage
import me.stappmus.messagegateway.modules.receiver.parsers.MmsAttachmentExtractor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date
import java.util.concurrent.TimeUnit

class ProcessIncomingMmsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val receiverSvc: ReceiverService by inject()
    private val logsService: LogsService by inject()
    private val mediaService: MediaService by inject()

    override suspend fun doWork(): Result {
        val transactionId = inputData.getString(KEY_TRANSACTION_ID)
            ?: return Result.failure()
        val messageId = inputData.getString(KEY_MESSAGE_ID)
        val senderAddress = inputData.getString(KEY_SENDER_ADDRESS)
        val subject = inputData.getString(KEY_SUBJECT)
        val contentClass = inputData.getString(KEY_CONTENT_CLASS)
        val messageSize = inputData.getLong(KEY_MESSAGE_SIZE, 0L)
        val receivedAtMs = inputData.getLong(KEY_RECEIVED_AT_MS, System.currentTimeMillis())
        val subscriptionId = inputData.getInt(KEY_SUBSCRIPTION_ID, -1).takeIf { it >= 0 }

        Log.i(
            TAG,
            "MMS processing started tx=$transactionId attempt=$runAttemptCount"
        )

        return try {
            val extractionResult = MmsAttachmentExtractor.extract(
                context = applicationContext,
                messageId = messageId,
                transactionId = transactionId,
                receivedAtEpochMs = receivedAtMs,
                senderAddress = senderAddress,
            )

            val attachments = mediaService.cacheIncomingAttachments(
                applicationContext,
                extractionResult.attachments,
            )

            if (attachments.isEmpty() && runAttemptCount < MAX_EMPTY_ATTACHMENT_RETRIES) {
                logsService.insert(
                    priority = LogEntry.Priority.WARN,
                    module = NAME,
                    message = "MMS attachments not ready, retrying",
                    context = mapOf(
                        "transactionId" to transactionId,
                        "attempt" to runAttemptCount,
                    )
                )
                return Result.retry()
            }

            val resolvedAddress = senderAddress
                ?.substringBefore('/')
                ?.takeIf { it.isNotBlank() }
                ?: "unknown"

            val message = InboxMessage.Mms(
                messageId = extractionResult.providerMessageId ?: messageId,
                transactionId = transactionId,
                subject = extractionResult.subject ?: subject,
                size = extractionResult.size ?: messageSize,
                contentClass = extractionResult.contentClass ?: contentClass,
                attachments = attachments,
                address = resolvedAddress,
                date = Date(receivedAtMs),
                subscriptionId = subscriptionId,
            )

            receiverSvc.process(applicationContext, message)

            logsService.insert(
                priority = LogEntry.Priority.DEBUG,
                module = NAME,
                message = "MMS processing completed",
                context = mapOf(
                    "transactionId" to transactionId,
                    "attachments" to attachments.size,
                    "attempt" to runAttemptCount,
                )
            )

            Result.success()
        } catch (e: Exception) {
            logsService.insert(
                priority = LogEntry.Priority.ERROR,
                module = NAME,
                message = "MMS processing failed: ${e.message ?: e.toString()}",
                context = mapOf(
                    "transactionId" to transactionId,
                    "attempt" to runAttemptCount,
                )
            )
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "ProcessIncomingMmsWorker"
        private const val TAG = "ProcessIncomingMms"

        private const val KEY_TRANSACTION_ID = "transaction_id"
        private const val KEY_MESSAGE_ID = "message_id"
        private const val KEY_SENDER_ADDRESS = "sender_address"
        private const val KEY_SUBJECT = "subject"
        private const val KEY_CONTENT_CLASS = "content_class"
        private const val KEY_MESSAGE_SIZE = "message_size"
        private const val KEY_RECEIVED_AT_MS = "received_at_ms"
        private const val KEY_SUBSCRIPTION_ID = "subscription_id"

        private const val MAX_EMPTY_ATTACHMENT_RETRIES = 5
        private const val INITIAL_DELAY_SECONDS = 2L

        fun start(
            context: Context,
            transactionId: String,
            messageId: String?,
            senderAddress: String,
            subject: String?,
            messageSize: Long,
            contentClass: String?,
            receivedAtMs: Long,
            subscriptionId: Int?,
        ) {
            val uniqueWorkName = "$NAME-$transactionId"
            val work = OneTimeWorkRequestBuilder<ProcessIncomingMmsWorker>()
                .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setInputData(
                    workDataOf(
                        KEY_TRANSACTION_ID to transactionId,
                        KEY_MESSAGE_ID to messageId,
                        KEY_SENDER_ADDRESS to senderAddress,
                        KEY_SUBJECT to subject,
                        KEY_CONTENT_CLASS to contentClass,
                        KEY_MESSAGE_SIZE to messageSize,
                        KEY_RECEIVED_AT_MS to receivedAtMs,
                        KEY_SUBSCRIPTION_ID to (subscriptionId ?: -1),
                    )
                )
                .build()

            Log.i(TAG, "Enqueueing worker tx=$transactionId id=${work.id}")
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP,
                work,
            )
        }
    }
}
