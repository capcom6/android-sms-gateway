package me.stappmus.messagegateway.modules.webhooks.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.gson.gson
import kotlinx.coroutines.runBlocking
import me.stappmus.messagegateway.BuildConfig
import me.stappmus.messagegateway.R
import me.stappmus.messagegateway.extensions.configure
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.notifications.NotificationsService
import me.stappmus.messagegateway.modules.webhooks.NAME
import me.stappmus.messagegateway.modules.webhooks.TemporaryStorage
import me.stappmus.messagegateway.modules.webhooks.WebhooksSettings
import me.stappmus.messagegateway.modules.webhooks.db.WebhookQueueRepository
import me.stappmus.messagegateway.modules.webhooks.domain.WebHookEventDTO
import me.stappmus.messagegateway.modules.webhooks.plugins.PayloadSingingPlugin
import org.json.JSONException
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Worker that sends webhook payloads to a URL.
 * @deprecated Remove after 2026-11-30
 */
class SendWebhookWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {

    private val notificationsSvc: NotificationsService by inject()
    private val logsSvc: LogsService by inject()

    private val settings: WebhooksSettings by inject()
    private val storage: TemporaryStorage by inject()

    override suspend fun doWork(): ListenableWorker.Result {
        Log.i(TAG, "Fallback doWork started, id=$id, runAttemptCount=$runAttemptCount")

        val storageKey = inputData.getString(INPUT_STORAGE_KEY)
        val payload = storageKey?.let {
            storage.get(it)
        } ?: inputData.getString("data")

        if (payload == null) {
            logsSvc.insert(
                priority = LogEntry.Priority.ERROR,
                module = NAME,
                message = "Empty payload",
                context = mapOf(
                    "url" to inputData.getString(INPUT_URL),
                    "storageKey" to inputData.getString(INPUT_STORAGE_KEY),
                )
            )
            return ListenableWorker.Result.failure()
        }

        return when (val result = sendData(payload)) {
            Result.Success -> {
                logsSvc.insert(
                    priority = LogEntry.Priority.INFO,
                    module = NAME,
                    message = "Webhook sent successfully",
                    context = mapOf(
                        "url" to inputData.getString(INPUT_URL),
                        "data" to payload,
                    )
                )

                storageKey?.let {
                    storage.remove(it)
                }
                ListenableWorker.Result.success()
            }

            is Result.Failure -> {
                logsSvc.insert(
                    priority = LogEntry.Priority.ERROR,
                    module = NAME,
                    message = "Webhook failed: ${result.error}",
                    context = mapOf(
                        "url" to inputData.getString(INPUT_URL),
                        "data" to payload,
                    )
                )

                storageKey?.let {
                    storage.remove(it)
                }
                ListenableWorker.Result.failure()
            }

            is Result.Retry -> {
                logsSvc.insert(
                    priority = LogEntry.Priority.WARN,
                    module = NAME,
                    message = "Webhook failed with retry: ${result.reason}",
                    context = mapOf(
                        "url" to inputData.getString(INPUT_URL),
                        "data" to payload,
                    )
                )
                ListenableWorker.Result.retry()
            }
        }
    }

    private suspend fun sendData(payload: String): Result {
        try {
            if (runAttemptCount >= settings.retryCount) {
                return Result.Failure("Retry limit exceeded")
            }

            val url = inputData.getString(INPUT_URL)
                ?: return Result.Failure("Empty url")
            val data = gson.fromJson(payload, JsonObject::class.java)
                ?: return Result.Failure("Empty data")

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(data)
            }

            val responseBody = runCatching { response.bodyAsText() }.getOrElse { "<unavailable>" }
            val responseBodyPreview = responseBody.take(500)

            Log.d(
                TAG,
                "Fallback worker response status=${response.status.value}, url=$url, " +
                        "bodyPreview=$responseBodyPreview"
            )

            if (response.status.value !in 200..299) {
                return Result.Retry("Status code: ${response.status.value}")
            }

            Result.Success
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            return Result.Failure(e.message ?: e.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
            return Result.Failure(e.message ?: e.toString())
        } catch (e: Throwable) {
            e.printStackTrace()
            Log.w(
                TAG,
                "Fallback worker request failed, url=${inputData.getString(INPUT_URL)}, " +
                        "errorType=${e::class.java.simpleName}, error=${e.message}"
            )
            return Result.Retry(e.message ?: e.toString())
        } finally {
            client.close()
        }

        return Result.Success
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun createForegroundInfo(): ForegroundInfo {
        val notificationId = NotificationsService.NOTIFICATION_ID_WEBHOOK_WORKER
        val notification = notificationsSvc.makeNotification(
            applicationContext,
            notificationId,
            applicationContext.getString(R.string.sending_webhook)
        )

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 5000
        }
        install(ContentNegotiation) {
            gson {
                configure()
            }
        }
        install(DefaultRequest) {
            userAgent("${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}")
        }
        install(PayloadSingingPlugin) {
            secretKeyProvider = { settings.signingKey }
        }
    }

    private sealed class Result {
        object Success : Result()
        class Failure(val error: String) : Result()
        class Retry(val reason: String) : Result()
    }

    companion object : KoinComponent {
        private val gson = GsonBuilder().configure().create()
        private const val TAG = "SendWebhookWorker"

        fun start(
            context: Context,
            url: String,
            data: WebHookEventDTO,
            internetRequired: Boolean,
        ) {
            val logsService = get<LogsService>()

            // Enqueue the webhook event to the persistent queue
            try {
                val queueRepository = get<WebhookQueueRepository>()

                // Enqueue to the persistent queue
                val queueId = runBlocking {
                    queueRepository.enqueueWebhook(
                        url = url,
                        payload = gson.toJson(data),
                    )
                }

                logsService.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Webhook enqueued to persistent queue",
                    context = mapOf(
                        "queueId" to queueId,
                        "url" to url,
                        "internetRequired" to internetRequired
                    )
                )

                // Start the queue processor worker to handle the enqueued webhook
                WebhookQueueProcessorWorker.start(
                    context = context,
                    internetRequired = internetRequired
                )

            } catch (e: Exception) {
                // Fallback to direct processing if queue enqueue fails
                logsService.insert(
                    priority = LogEntry.Priority.WARN,
                    module = NAME,
                    message = "Queue enqueue failed, falling back to direct processing: ${e.message}",
                    context = mapOf(
                        "error" to e.toString(),
                        "url" to url
                    )
                )

                // Store the payload in temporary storage
                get<TemporaryStorage>().put(data.id, gson.toJson(data))

                // Fallback to original behavior
                val work = OneTimeWorkRequestBuilder<SendWebhookWorker>()
                    .setInputData(
                        workDataOf(
                            INPUT_URL to url,
                            INPUT_STORAGE_KEY to data.id,
                        )
                    )
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                    .build()

                WorkManager.getInstance(context)
                    .enqueue(work)
            }
        }

        private const val INPUT_URL = "url"
        private const val INPUT_STORAGE_KEY = "storageKey"
    }
}
