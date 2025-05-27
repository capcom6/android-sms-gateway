package me.capcom.smsgateway.modules.webhooks.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.notifications.NotificationsService
import me.capcom.smsgateway.modules.webhooks.WebHooksService
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class ReviewWebhooksWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {

    private val webHooksService: WebHooksService by inject()
    private val notificationsService: NotificationsService by inject()

    override suspend fun doWork(): Result {
        val webhooks = webHooksService.select(null)
        val smsReceivedWebhooks = webhooks.filter { it.event == WebHookEvent.SmsReceived }
        if (smsReceivedWebhooks.isEmpty()) {
            return Result.success()
        }

        notificationsService.notify(
            applicationContext,
            NotificationsService.NOTIFICATION_ID_SMS_RECEIVED_WEBHOOK,
            applicationContext.getString(
                R.string.you_have_sms_received_webhooks_registered_please_review_them_to_avoid_any_security_risks,
                smsReceivedWebhooks.size
            )
        )

        return Result.success()
    }

    companion object {
        private const val NAME = "ReviewWebhooksWorker"

        fun start(context: Context) {
            val work = PeriodicWorkRequestBuilder<ReviewWebhooksWorker>(
                30,
                TimeUnit.DAYS
            )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    work
                )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(NAME)
        }
    }
}