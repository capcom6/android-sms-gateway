package me.capcom.smsgateway.modules.gateway.inbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.R
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.gateway.GatewayApi
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.gateway.MODULE_NAME
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.notifications.NotificationsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date
import java.util.concurrent.TimeUnit

class InboxUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val notificationsSvc: NotificationsService by inject()
    private val logsSvc: LogsService by inject()
    private val repository: InboxUploadRepository by inject()
    private val gatewayService: GatewayService by inject()
    private val gson = GsonBuilder().configure().create()

    companion object {
        private const val BATCH_SIZE = 100
        private const val MIN_BACKOFF_DELAY_MS = 5000L
        private const val WORK_NAME = "inbox_upload_processor"

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<InboxUploadWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    MIN_BACKOFF_DELAY_MS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setForeground(getForegroundInfo())
        } catch (_: Exception) {
        }

        return@withContext try {
            processPending()
            Result.success()
        } catch (e: Exception) {
            logsSvc.insert(
                LogEntry.Priority.ERROR,
                MODULE_NAME,
                "Inbox upload failed: ${e.message}",
                mapOf("error" to e.toString(), "stackTrace" to e.stackTraceToString()),
            )
            Result.retry()
        }
    }

    private suspend fun processPending() {
        while (true) {
            val pending = repository.getPending(BATCH_SIZE)
            if (pending.isEmpty()) return
            uploadRecursive(pending)
        }
    }

    private suspend fun uploadRecursive(entities: List<InboxUploadEntity>) {
        if (entities.isEmpty()) return

        val ids = entities.map { it.id }
        val messages = entities.map { entity -> buildMessage(entity) }

        try {
            gatewayService.uploadInbox(messages)
            repository.complete(ids)
        } catch (e: Exception) {
            if (entities.size == 1) {
                logsSvc.insert(
                    LogEntry.Priority.ERROR,
                    MODULE_NAME,
                    "Inbox upload failed: ${e.message}",
                    mapOf("message" to entities[0], "error" to e.toString()),
                )
                repository.complete(ids)
            } else {
                val mid = entities.size / 2
                uploadRecursive(entities.subList(0, mid))
                uploadRecursive(entities.subList(mid, entities.size))
            }
        }
    }

    private fun buildMessage(entity: InboxUploadEntity): GatewayApi.InboxMessage {
        val attachments = entity.attachmentsEncrypted?.let { serializedAttachments ->
            gson.fromJson(serializedAttachments, Array<EncryptedAttachment>::class.java).map {
                GatewayApi.InboxMessageAttachment(
                    it.partId,
                    it.contentType,
                    it.name,
                    it.size,
                    it.data
                )
            }
        }

        return GatewayApi.InboxMessage(
            id = entity.messageId,
            type = entity.type,
            sender = entity.sender,
            recipient = entity.recipient,
            simNumber = entity.simNumber,
            content = entity.contentEncrypted,
            isEncrypted = true,
            createdAt = Date(entity.messageCreatedAt),
            attachments = attachments,
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notificationsSvc.makeNotification(
            applicationContext,
            NotificationsService.NOTIFICATION_ID_INBOX_WORKER,
            applicationContext.getString(R.string.processing_inbox_queue),
        )
        return ForegroundInfo(NotificationsService.NOTIFICATION_ID_INBOX_WORKER, notification)
    }
}
