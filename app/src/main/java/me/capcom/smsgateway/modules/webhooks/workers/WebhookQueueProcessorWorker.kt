package me.capcom.smsgateway.modules.webhooks.workers

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
import com.google.gson.JsonObject
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.R
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.notifications.NotificationsService
import me.capcom.smsgateway.modules.webhooks.NAME
import me.capcom.smsgateway.modules.webhooks.WebhooksSettings
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueEntity
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueRepository
import me.capcom.smsgateway.modules.webhooks.plugins.PayloadSingingPlugin
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
        private const val MIN_BACKOFF_DELAY_MS = 5000L

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

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
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
            return@withContext try {
                val url = webhook.url
                val data = gson.fromJson(webhook.payload, JsonObject::class.java)
                    ?: return@withContext false

                val response = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(data)
                }

                if (response.status.value in 200..299) {
                    logsSvc.insert(
                        priority = LogEntry.Priority.INFO,
                        module = NAME,
                        message = "Webhook sent successfully",
                        context = mapOf(
                            "webhookId" to webhook.id,
                            "url" to url,
                            "retryCount" to webhook.retryCount
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
                            "retryCount" to webhook.retryCount
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
                logsSvc.insert(
                    priority = LogEntry.Priority.WARN,
                    module = NAME,
                    message = "Webhook network error: ${e.message}",
                    context = mapOf(
                        "webhookId" to webhook.id,
                        "error" to e.message,
                        "retryCount" to webhook.retryCount
                    )
                )
                false // Network errors should trigger retry
            } finally {
                client.close()
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

        return ForegroundInfo(notificationId, notification)
    }

    /**
     * HTTP client with the same configuration as the original SendWebhookWorker.
     */
    private val client
        get() = HttpClient(OkHttp) {
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

    private val gson = GsonBuilder().configure().create()
}