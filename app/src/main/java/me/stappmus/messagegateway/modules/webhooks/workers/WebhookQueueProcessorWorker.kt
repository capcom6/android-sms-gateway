package me.stappmus.messagegateway.modules.webhooks.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.stappmus.messagegateway.BuildConfig
import me.stappmus.messagegateway.R
import me.stappmus.messagegateway.extensions.configure
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.notifications.NotificationsService
import me.stappmus.messagegateway.modules.webhooks.NAME
import me.stappmus.messagegateway.modules.webhooks.WebhooksSettings
import me.stappmus.messagegateway.modules.webhooks.db.WebhookQueueEntity
import me.stappmus.messagegateway.modules.webhooks.db.WebhookQueueRepository
import me.stappmus.messagegateway.modules.webhooks.plugins.PayloadSingingPlugin
import org.json.JSONException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Worker that processes webhook events from a persistent queue.
 * This replaces the per-event worker approach with a single persistent worker
 * that handles multiple events with priority-based processing.
 */
class WebhookQueueProcessorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val notificationsSvc: NotificationsService by inject()
    private val logsSvc: LogsService by inject()
    private val webhookRepository: WebhookQueueRepository by inject()
    private val settings: WebhooksSettings by inject()

    companion object {
        private const val BATCH_SIZE = 20
        private const val PROCESSING_TIMEOUT_MS = 30000L
        private const val MIN_BACKOFF_DELAY_MS = WorkRequest.MIN_BACKOFF_MILLIS
        private const val TAG = "WebhookQueueProcessor"

        // Work name for this worker
        private const val WORK_NAME = "webhook_queue_processor"

        /**
         * Start the queue processor worker.
         * This will schedule the worker to run periodically.
         */
        fun start(
            context: Context,
            internetRequired: Boolean = false
        ) {
            val workRequest = OneTimeWorkRequestBuilder<WebhookQueueProcessorWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    MIN_BACKOFF_DELAY_MS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            Log.i(
                TAG,
                "Enqueueing worker=${WebhookQueueProcessorWorker::class.java.name}, id=${workRequest.id}, " +
                        "policy=APPEND, internetRequired=$internetRequired, " +
                        "networkConstraint=NOT_REQUIRED, backoffMs=$MIN_BACKOFF_DELAY_MS"
            )

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
        }

        /**
         * Stop any running queue processor workers.
         */
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(
                TAG,
                "doWork started, id=$id, runAttemptCount=$runAttemptCount"
            )

            logsSvc.insert(
                priority = LogEntry.Priority.DEBUG,
                module = NAME,
                message = "Starting webhook queue processing",
                context = mapOf(
                    "attempt" to runAttemptCount,
                )
            )

            // Get foreground info early to ensure proper service startup
            try {
                setForeground(getForegroundInfo())
            } catch (e: Exception) {
                logsSvc.insert(
                    priority = LogEntry.Priority.WARN,
                    module = NAME,
                    message = "Failed to set foreground: ${e.message}",
                    context = mapOf("error" to e.toString())
                )
            }

            do {
                // Process the queue with priority handling
                val processedCount = withContext(NonCancellable) {
                    processWebhookQueue()
                }

                logsSvc.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Queue iteration completed",
                    context = mapOf(
                        "processedCount" to processedCount,
                    )
                )

                if (processedCount == 0) {
                    delay(MIN_BACKOFF_DELAY_MS)
                }
            } while (webhookRepository.hasScheduledWebhooks() && isActive)

            if (isActive) {
                webhookRepository.cleanupOldEntries()
            }

            Log.i(
                TAG,
                "doWork completed successfully, id=$id"
            )

            logsSvc.insert(
                priority = LogEntry.Priority.INFO,
                module = NAME,
                message = "Webhook queue processing completed",
                context = mapOf(
                    "attempt" to runAttemptCount,
                )
            )

            return@withContext Result.success()
        } catch (e: Exception) {
            logsSvc.insert(
                priority = LogEntry.Priority.ERROR,
                module = NAME,
                message = "Webhook queue processing failed: ${e.message}",
                context = mapOf(
                    "error" to e.toString(),
                    "stackTrace" to e.stackTraceToString()
                )
            )

            Log.e(
                TAG,
                "doWork failed, id=$id, error=${e.message}",
                e,
            )

            // Use linear backoff for retries
            return@withContext Result.retry()
        }
    }

    /**
     * Process webhook events from the queue with priority handling.
     */
    private suspend fun processWebhookQueue(): Int = withContext(Dispatchers.IO) {
        var totalProcessed = 0

        try {
            // Recover any stuck webhooks first
            webhookRepository.recoverStuckWebhooks()

            totalProcessed = processBatch(BATCH_SIZE)

        } catch (e: Exception) {
            logsSvc.insert(
                priority = LogEntry.Priority.ERROR,
                module = NAME,
                message = "Error during webhook queue processing: ${e.message}",
                context = mapOf(
                    "error" to e.toString(),
                )
            )
            throw e
        }

        return@withContext totalProcessed
    }

    /**
     * Process a batch of webhooks for a specific priority.
     */
    private suspend fun processBatch(batchSize: Int): Int =
        withContext(Dispatchers.IO) {
            try {
                // Get pending webhooks for this priority
                val webhooks = webhookRepository.getPendingWebhooks(limit = batchSize)

                if (webhooks.isEmpty()) {
                    return@withContext 0
                }

                logsSvc.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Processing ${webhooks.size} webhooks",
                    context = mapOf(
                        "count" to webhooks.size,
                        "webhookIds" to webhooks.map { it.url }
                    )
                )

                var processedCount = 0
                var failedCount = 0

                for (webhook in webhooks) {
                    try {
                        // Start processing this webhook
                        webhookRepository.startProcessing(webhook.id)

                        // Send the webhook
                        val success = sendWebhook(webhook)

                        if (success) {
                            // Mark as completed
                            webhookRepository.completeWebhook(webhook.id)
                            processedCount++
                        } else {
                            // Schedule retry
                            handleWebhookFailure(webhook.id, "Processing failed")
                            failedCount++
                        }
                    } catch (e: Exception) {
                        logsSvc.insert(
                            priority = LogEntry.Priority.ERROR,
                            module = NAME,
                            message = "Error processing webhook ${webhook.id}: ${e.message}",
                            context = mapOf(
                                "webhookId" to webhook.id,
                                "error" to e.toString()
                            )
                        )
                        handleWebhookFailure(webhook.id, e.message)
                        failedCount++
                    }
                }

                logsSvc.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Batch processing completed",
                    context = mapOf(
                        "totalInBatch" to webhooks.size,
                        "processed" to processedCount,
                        "failed" to failedCount
                    )
                )

                return@withContext processedCount

            } catch (e: Exception) {
                logsSvc.insert(
                    priority = LogEntry.Priority.ERROR,
                    module = NAME,
                    message = "Error processing batch: ${e.message}",
                    context = mapOf("error" to e.toString())
                )
                throw e
            }
        }

    /**
     * Send a webhook using the same HTTP client configuration as the original worker.
     */
    private suspend fun sendWebhook(webhook: WebhookQueueEntity): Boolean =
        withContext(Dispatchers.IO) {
            val httpClient = createHttpClient()
            return@withContext try {
                val url = webhook.url
                val data = gson.fromJson(webhook.payload, JsonObject::class.java)
                    ?: return@withContext false

                Log.d(
                    TAG,
                    "Dispatching webhook id=${webhook.id}, url=$url, retryCount=${webhook.retryCount}"
                )

                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(data)
                }

                val responseBody = runCatching { response.bodyAsText() }.getOrElse { "<unavailable>" }
                val responseBodyPreview = responseBody.take(500)

                Log.d(
                    TAG,
                    "Webhook response id=${webhook.id}, status=${response.status.value}, " +
                            "bodyPreview=$responseBodyPreview"
                )

                if (response.status.value in 200..299) {
                    logsSvc.insert(
                        priority = LogEntry.Priority.INFO,
                        module = NAME,
                        message = "Webhook sent successfully",
                        context = mapOf(
                            "webhookId" to webhook.id,
                            "url" to url,
                            "retryCount" to webhook.retryCount,
                            "statusCode" to response.status.value
                        )
                    )
                    true
                } else {
                    logsSvc.insert(
                        priority = LogEntry.Priority.WARN,
                        module = NAME,
                        message = "Webhook failed with status ${response.status.value}",
                        context = mapOf(
                            "webhookId" to webhook.id,
                            "url" to url,
                            "statusCode" to response.status.value,
                            "retryCount" to webhook.retryCount,
                            "responseBody" to responseBodyPreview
                        )
                    )
                    false
                }

            } catch (e: IllegalArgumentException) {
                logsSvc.insert(
                    priority = LogEntry.Priority.ERROR,
                    module = NAME,
                    message = "Webhook invalid argument: ${e.message}",
                    context = mapOf(
                        "webhookId" to webhook.id,
                        "error" to e.message
                    )
                )
                false
            } catch (e: JSONException) {
                logsSvc.insert(
                    priority = LogEntry.Priority.ERROR,
                    module = NAME,
                    message = "Webhook JSON error: ${e.message}",
                    context = mapOf(
                        "webhookId" to webhook.id,
                        "error" to e.message
                    )
                )
                false
            } catch (e: Exception) {
                val isCleartextBlocked = e.message?.contains("CLEARTEXT communication", ignoreCase = true) == true
                logsSvc.insert(
                    priority = LogEntry.Priority.WARN,
                    module = NAME,
                    message = "Webhook network error: ${e.message}",
                    context = mapOf(
                        "webhookId" to webhook.id,
                        "error" to e.message,
                        "errorType" to e::class.java.simpleName,
                        "cleartextBlocked" to isCleartextBlocked,
                        "retryCount" to webhook.retryCount
                    )
                )
                if (isCleartextBlocked) {
                    logsSvc.insert(
                        priority = LogEntry.Priority.WARN,
                        module = NAME,
                        message = "Webhook blocked by cleartext policy. Use HTTPS or insecure build for HTTP testing.",
                        context = mapOf(
                            "webhookId" to webhook.id,
                            "url" to webhook.url,
                        )
                    )
                }
                false // Network errors should trigger retry
            } finally {
                httpClient.close()
            }
        }

    /**
     * Handle webhook failure and schedule retry if appropriate.
     */
    private suspend fun handleWebhookFailure(webhookId: Long, error: String?) {
        try {
            webhookRepository.scheduleRetry(
                webhookId = webhookId,
                error = error,
                maxRetries = settings.retryCount,
                baseDelayMs = MIN_BACKOFF_DELAY_MS
            )
        } catch (e: Exception) {
            logsSvc.insert(
                priority = LogEntry.Priority.ERROR,
                module = NAME,
                message = "Failed to schedule retry for webhook $webhookId: ${e.message}",
                context = mapOf(
                    "webhookId" to webhookId,
                    "originalError" to error,
                    "retryError" to e.toString()
                )
            )
            // If retry scheduling fails, mark as permanently failed
            webhookRepository.permanentlyFailWebhook(webhookId, error ?: "Unknown error")
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationId = NotificationsService.NOTIFICATION_ID_WEBHOOK_WORKER
        val notification = notificationsSvc.makeNotification(
            applicationContext,
            notificationId,
            applicationContext.getString(R.string.processing_webhook_queue)
        )

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    /**
     * HTTP client with the same configuration as the original SendWebhookWorker.
     */
    private fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = PROCESSING_TIMEOUT_MS
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
    }

    private val gson = GsonBuilder().configure().create()
}
