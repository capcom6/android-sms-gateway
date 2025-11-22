package me.capcom.smsgateway.modules.webhooks.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.gson.gson
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.R
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.notifications.NotificationsService
import me.capcom.smsgateway.modules.webhooks.NAME
import me.capcom.smsgateway.modules.webhooks.TemporaryStorage
import me.capcom.smsgateway.modules.webhooks.WebhooksSettings
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEventDTO
import me.capcom.smsgateway.modules.webhooks.db.WebhookPriority
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueRepository
import me.capcom.smsgateway.modules.webhooks.plugins.PayloadSingingPlugin
import org.json.JSONException
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class SendWebhookWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {

    private val notificationsSvc: NotificationsService by inject()
    private val logsSvc: LogsService by inject()

    private val settings: WebhooksSettings by inject()
    private val storage: TemporaryStorage by inject()

    override suspend fun doWork(): ListenableWorker.Result {
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

        return ForegroundInfo(notificationId, notification)
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
        
        fun start(
            context: Context,
            url: String,
            data: WebHookEventDTO,
            internetRequired: Boolean,
            priority: WebhookPriority = WebhookPriority.NORMAL
        ) {
            // Store the payload in temporary storage (for backward compatibility)
            get<TemporaryStorage>().put(data.id, gson.toJson(data))

            // Enqueue the webhook event to the persistent queue
            try {
                val queueRepository = get<WebhookQueueRepository>()
                val logsService = get<LogsService>()
                
                // Determine priority based on whether it's expedited
                val finalPriority = if (internetRequired) {
                    // For internet-required webhooks, use higher priority
                    when {
                        priority == WebhookPriority.CRITICAL -> WebhookPriority.CRITICAL
                        priority == WebhookPriority.HIGH -> WebhookPriority.HIGH
                        else -> WebhookPriority.HIGH // Upgrade to high priority
                    }
                } else {
                    priority
                }
                
                // Enqueue to the persistent queue
                val queueId = runBlocking {
                    queueRepository.enqueueWebhook(
                        webhookId = url,
                        payload = gson.toJson(data),
                        priority = finalPriority
                    )
                }
                
                logsService.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Webhook enqueued to persistent queue",
                    context = mapOf(
                        "queueId" to queueId,
                        "url" to url,
                        "priority" to finalPriority.displayName,
                        "internetRequired" to internetRequired
                    )
                )
                
                // Start the queue processor worker to handle the enqueued webhook
                WebhookQueueProcessorWorker.start(
                    context = context,
                    forceStart = true,
                    immediateProcessing = internetRequired // Process immediately if internet is required
                )
                
            } catch (e: Exception) {
                // Fallback to direct processing if queue enqueue fails
                val logsService = get<LogsService>()
                logsService.insert(
                    priority = LogEntry.Priority.WARN,
                    module = NAME,
                    message = "Queue enqueue failed, falling back to direct processing: ${e.message}",
                    context = mapOf(
                        "error" to e.toString(),
                        "url" to url
                    )
                )
                
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
                    .apply {
                        if (internetRequired) {
                            setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build()
                            )
                        }
                    }
                    .build()

                WorkManager.getInstance(context)
                    .enqueue(work)
            }
        }

        private const val INPUT_URL = "url"
        private const val INPUT_STORAGE_KEY = "storageKey"
    }
}