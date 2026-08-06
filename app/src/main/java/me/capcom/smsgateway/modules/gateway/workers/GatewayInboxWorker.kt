package me.capcom.smsgateway.modules.gateway.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import me.capcom.smsgateway.modules.encryption.IncomingMessageEncryptor
import me.capcom.smsgateway.modules.gateway.GatewayApi
import me.capcom.smsgateway.modules.gateway.GatewayInboxUploader
import me.capcom.smsgateway.modules.gateway.GatewayInboxUploadOutcome
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.gateway.IncomingMessageReader
import me.capcom.smsgateway.modules.gateway.InboxUploadFilter
import me.capcom.smsgateway.modules.gateway.SmsProviderReader
import me.capcom.smsgateway.modules.gateway.StoredAttachmentReader
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.mms.MmsAttachmentStorage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * One-shot encrypted inbox upload (plan A-A4). Enqueued as UNIQUE work so
 * bursts coalesce (same name + REPLACE policy - PullMessagesWorker pattern).
 *
 * doWork() maps the pure [GatewayInboxUploader] outcome:
 * - ALL_UPLOADED / MISSING_PASSPHRASE / MISSING_TOKEN / PERMANENT_FAILURE ->
 *   Result.success() (permanent errors are NOT retried; rows stay pending and
 *   are re-attempted on the next trigger - see GatewayInboxUploader docs).
 * - RETRY_REQUIRED -> Result.retry() with the exponential backoff set at
 *   enqueue time (WorkRequest.MIN_BACKOFF_MILLIS, SendStateWorker pattern).
 *
 * Input data (all optional): since/until epoch millis, types as a
 * comma-separated enum-name list. No input = ALL pending rows.
 */
class GatewayInboxWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val encryptionSettings: EncryptionSettings by inject()
    private val gatewaySettings: GatewaySettings by inject()
    private val repository: IncomingMessagesRepository by inject()
    private val logsService: LogsService by inject()
    private val attachmentStorage: MmsAttachmentStorage by inject()

    override suspend fun doWork(): Result {
        val filter = uploadFilterFromData(inputData)
        return try {
            val outcome = withContext(Dispatchers.IO) {
                val encryptor = IncomingMessageEncryptor(
                    service = encryptionService,
                    warn = { message ->
                        logsService.insert(
                            LogEntry.Priority.WARN,
                            MODULE_NAME,
                            message
                        )
                    },
                )

                val api = GatewayApi(gatewaySettings.serverUrl, gatewaySettings.privateToken)

                GatewayInboxUploader(
                    passphrase = { encryptionSettings.passphrase },
                    deviceToken = { gatewaySettings.registrationInfo?.token },
                    reader = IncomingMessageReader { message ->
                        SmsProviderReader.read(applicationContext, message)
                    },
                    storedAttachments = StoredAttachmentReader { messageId, partId ->
                        attachmentStorage.find(messageId, partId)?.file
                    },
                    repository = repository,
                    encryptor = encryptor,
                    logsService = logsService,
                    uploadChunk = { token, chunk -> api.uploadInbox(token, chunk) },
                ).upload(filter)
            }

            when (outcome) {
                GatewayInboxUploadOutcome.RETRY_REQUIRED -> Result.retry()
                else -> Result.success()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
            Result.retry()
        }
    }

    private val encryptionService: EncryptionService by inject()

    companion object {
        const val NAME = "GatewayInboxWorker"

        private const val MODULE_NAME = "gateway"

        /**
         * Enqueues one unique one-shot upload. REPLACE policy coalesces bursts:
         * a second trigger while one run is pending supersedes the input, the
         * pending run still executes (filters re-read at run start).
         */
        fun start(
            context: Context,
            since: Long? = null,
            until: Long? = null,
            types: Set<IncomingMessageType>? = null,
        ) {
            val data = Data.Builder()
                .put(KEY_SINCE, since)
                .put(KEY_UNTIL, until)
                .put(KEY_TYPES, types?.joinToString(","))
                .build()

            val request = OneTimeWorkRequestBuilder<GatewayInboxWorker>()
                .setInputData(data)
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
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Deserializes the optional upload filter from the worker input data. */
internal fun uploadFilterFromData(data: Data): InboxUploadFilter =
    InboxUploadFilter(
        since = data.getLong(KEY_SINCE, 0L).takeIf { data.hasKeyWithValueOfType(KEY_SINCE, Long::class.javaObjectType) },
        until = data.getLong(KEY_UNTIL, 0L).takeIf { data.hasKeyWithValueOfType(KEY_UNTIL, Long::class.javaObjectType) },
        types = data.getString(KEY_TYPES)?.split(',')?.map { IncomingMessageType.valueOf(it) }?.toSet(),
    )

internal const val KEY_SINCE = "since"
internal const val KEY_UNTIL = "until"
internal const val KEY_TYPES = "types"
