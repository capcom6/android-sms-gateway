package me.capcom.smsgateway.modules.webhooks

import android.content.Context
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
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
    private val enqueueLock = Any()
    private val gson = GsonBuilder().configure().create()

    fun enqueue(
        context: Context,
        url: String,
        data: WebHookEventDTO,
        internetRequired: Boolean,
    ) {
        synchronized(enqueueLock) {
            val queueId = runBlocking {
                queueRepository.enqueueWebhook(
                    url = url,
                    payload = gson.toJson(data),
                )
            }

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
                context = context,
                internetRequired = internetRequired,
                policy = WebhookQueueProcessorWorker.resolvePolicy(context),
            )
        }
    }
}
