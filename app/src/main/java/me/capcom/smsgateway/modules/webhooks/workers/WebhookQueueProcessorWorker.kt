package me.capcom.smsgateway.modules.webhooks.workers

import android.content.Context
import androidx.work.*
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
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.R
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.notifications.NotificationsService
import me.capcom.smsgateway.modules.webhooks.NAME
import me.capcom.smsgateway.modules.webhooks.WebhooksSettings
import me.capcom.smsgateway.modules.webhooks.db.*
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
        private const val MAX_EXPEDITED_BATCH_SIZE = 5
        private const val MAX_NORMAL_BATCH_SIZE = 20
        private const val PROCESSING_TIMEOUT_MS = 30000L
        private const val MIN_BACKOFF_DELAY_MS = 5000L
        private const val MAX_WORKER_FREQUENCY_MS = 30000L // Don't schedule too frequently
        
        private const val INPUT_FORCE_START = "force_start"
        private const val INPUT_IMMEDIATE_PROCESSING = "immediate_processing"
        
        // Work name for this worker
        const val WORK_NAME = "webhook_queue_processor"
        
        /**
         * Start the queue processor worker.
         * This will schedule the worker to run periodically.
         */
        fun start(context: Context, forceStart: Boolean = false, immediateProcessing: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<WebhookQueueProcessorWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MIN_BACKOFF_DELAY_MS,
                    TimeUnit.MILLISECONDS
                )
                .apply {
                    if (immediateProcessing) {
                        setInitialDelay(0, TimeUnit.MILLISECONDS)
                    } else {
                        setInitialDelay(5, TimeUnit.SECONDS) // Small delay to batch multiple enqueues
                    }
                }
                .addTag(WORK_NAME)
                .setInputData(
                    workDataOf(
                        INPUT_FORCE_START to forceStart,
                        INPUT_IMMEDIATE_PROCESSING to immediateProcessing
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP, // Keep existing work, don't start duplicate
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
                    "forceStart" to inputData.getBoolean(INPUT_FORCE_START, false)
                )
            )

            // Get foreground info early to ensure proper service startup
            getForegroundInfo()

            // Check if we should force processing
            val forceStart = inputData.getBoolean(INPUT_FORCE_START, false)
            
            // Process the queue with priority handling
            val processedCount = processWebhookQueue()
            
            // Determine if we should schedule another run
            val shouldReschedule = shouldReschedule(processedCount)
            
            return@withContext if (shouldReschedule) {
                logsSvc.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Queue processing completed, scheduling next run",
                    context = mapOf(
                        "processedCount" to processedCount,
                        "shouldReschedule" to true
                    )
                )
                
                // Schedule next run with appropriate delay
                scheduleNextRun()
                Result.success()
            } else {
                logsSvc.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Queue processing completed, no more items to process",
                    context = mapOf(
                        "processedCount" to processedCount,
                        "shouldReschedule" to false
                    )
                )
                Result.success()
            }

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
            
            // Use exponential backoff for retries
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
            
            // Process expedited events first (batch size: MAX_EXPEDITED_BATCH_SIZE)
            val expeditedCount = processPriorityBatch(WebhookPriority.CRITICAL, MAX_EXPEDITED_BATCH_SIZE)
            if (expeditedCount > 0) {
                logsSvc.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Processed $expeditedCount expedited webhooks",
                    context = mapOf("processedCount" to expeditedCount)
                )
            }
            totalProcessed += expeditedCount
            
            // Process high priority events
            val highCount = processPriorityBatch(WebhookPriority.HIGH, MAX_EXPEDITED_BATCH_SIZE)
            if (highCount > 0) {
                logsSvc.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Processed $highCount high priority webhooks",
                    context = mapOf("processedCount" to highCount)
                )
            }
            totalProcessed += highCount
            
            // Process normal priority events in larger batches
            val normalCount = processPriorityBatch(WebhookPriority.NORMAL, MAX_NORMAL_BATCH_SIZE)
            if (normalCount > 0) {
                logsSvc.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Processed $normalCount normal priority webhooks",
                    context = mapOf("processedCount" to normalCount)
                )
            }
            totalProcessed += normalCount
            
            // Process low priority events
            val lowCount = processPriorityBatch(WebhookPriority.LOW, MAX_NORMAL_BATCH_SIZE)
            if (lowCount > 0) {
                logsSvc.insert(
                    priority = LogEntry.Priority.DEBUG,
                    module = NAME,
                    message = "Processed $lowCount low priority webhooks",
                    context = mapOf("processedCount" to lowCount)
                )
            }
            totalProcessed += lowCount

            logsSvc.insert(
                priority = LogEntry.Priority.INFO,
                module = NAME,
                message = "Webhook queue processing completed",
                context = mapOf(
                    "totalProcessed" to totalProcessed,
                    "batchSizes" to mapOf(
                        "expedited" to expeditedCount,
                        "high" to highCount,
                        "normal" to normalCount,
                        "low" to lowCount
                    )
                )
            )

        } catch (e: Exception) {
            logsSvc.insert(
                priority = LogEntry.Priority.ERROR,
                module = NAME,
                message = "Error during webhook queue processing: ${e.message}",
                context = mapOf(
                    "error" to e.toString(),
                    "processedSoFar" to totalProcessed
                )
            )
            throw e
        }
        
        return@withContext totalProcessed
    }

    /**
     * Process a batch of webhooks for a specific priority.
     */
    private suspend fun processPriorityBatch(priority: WebhookPriority, batchSize: Int): Int = withContext(Dispatchers.IO) {
        try {
            // Get pending webhooks for this priority
            val webhooks = webhookRepository.getPendingWebhooksByPriority(priority, batchSize)
            
            if (webhooks.isEmpty()) {
                return@withContext 0
            }

            logsSvc.insert(
                priority = LogEntry.Priority.DEBUG,
                module = NAME,
                message = "Processing ${webhooks.size} ${priority.displayName} priority webhooks",
                context = mapOf(
                    "priority" to priority.displayName,
                    "count" to webhooks.size,
                    "webhookIds" to webhooks.map { it.webhookId }
                )
            )

            var processedCount = 0
            var failedCount = 0

            for (webhook in webhooks) {
                try {
                    // Start processing this webhook
                    if (webhookRepository.startProcessing(webhook.id)) {
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
                    } else {
                        // Could not start processing, skip this webhook
                        logsSvc.insert(
                            priority = LogEntry.Priority.WARN,
                            module = NAME,
                            message = "Could not start processing webhook ${webhook.id}",
                            context = mapOf("webhookId" to webhook.id)
                        )
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
                message = "Batch processing completed for ${priority.displayName} priority",
                context = mapOf(
                    "priority" to priority.displayName,
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
                message = "Error processing ${priority.displayName} priority batch: ${e.message}",
                context = mapOf("error" to e.toString())
            )
            throw e
        }
    }

    /**
     * Send a webhook using the same HTTP client configuration as the original worker.
     */
    private suspend fun sendWebhook(webhook: WebhookQueueEntity): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = webhook.webhookId
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
        } catch (e: Throwable) {
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

    /**
     * Determine if we should schedule another run based on the processed count and queue state.
     */
    private suspend fun shouldReschedule(processedCount: Int): Boolean {
        return try {
            // If we processed items, check if there are more pending
            if (processedCount > 0) {
                val statistics = webhookRepository.getQueueStatistics()
                statistics.pending > 0 || statistics.processing > 0
            } else {
                // Even if we didn't process anything, check if there are items ready for retry
                val statistics = webhookRepository.getQueueStatistics()
                statistics.pending > 0 || statistics.processing > 0
            }
        } catch (e: Exception) {
            logsSvc.insert(
                priority = LogEntry.Priority.ERROR,
                module = NAME,
                message = "Error checking queue state for rescheduling: ${e.message}",
                context = mapOf("error" to e.toString())
            )
            true // Default to rescheduling in case of error
        }
    }

    /**
     * Schedule the next worker run.
     */
    private fun scheduleNextRun() {
        try {
            // Use WorkManager to schedule the next run
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<WebhookQueueProcessorWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MIN_BACKOFF_DELAY_MS,
                    TimeUnit.MILLISECONDS
                )
                .setInitialDelay(MAX_WORKER_FREQUENCY_MS, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            logsSvc.insert(
                priority = LogEntry.Priority.ERROR,
                module = NAME,
                message = "Failed to schedule next worker run: ${e.message}",
                context = mapOf("error" to e.toString())
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    /**
     * Creates an instance of ForegroundInfo for the worker notification.
     */
    private fun createForegroundInfo(): ForegroundInfo {
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
    private val client = HttpClient(OkHttp) {
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