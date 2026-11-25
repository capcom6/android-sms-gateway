package me.capcom.smsgateway.modules.webhooks

import android.content.Context
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueRepository
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEventDTO
import me.capcom.smsgateway.modules.webhooks.workers.WebhookQueueProcessorWorker

class WebhookQueueDispatcher(
    private val queueRepository: WebhookQueueRepository,
    private val logsService: LogsService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = GsonBuilder().configure().create()

    fun enqueue(
        context: Context,
        url: String,
        data: WebHookEventDTO,
        internetRequired: Boolean,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            WebhookQueueCoordinator.mutex.withLock {
                try {
                    val queueId = queueRepository.enqueueWebhook(
                        url = url,
                        payload = gson.toJson(data),
                    )

                    logsService.insert(
                        LogEntry.Priority.DEBUG,
                        NAME,
                        "Webhook enqueued to persistent queue",
                        mapOf(
                            "queueId" to queueId,
                            "url" to url,
                            "internetRequired" to internetRequired
                        )
                    )

                    WebhookQueueProcessorWorker.start(
                        context = appContext,
                        internetRequired = internetRequired,
                        policy = WebhookQueueProcessorWorker.resolvePolicy(appContext),
                    )
                } catch (e: Exception) {
                    logsService.insert(
                        LogEntry.Priority.ERROR,
                        NAME,
                        "Failed to queue webhook event: ${e.message}",
                        mapOf(
                            "error" to e.toString(),
                            "url" to url
                        )
                    )
                }
            }
        }
    }
}
