package me.capcom.smsgateway.modules.gateway.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.gateway.GatewayApi
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.webhooks.WebHooksService
import me.capcom.smsgateway.modules.webhooks.domain.WebHookDTO
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.TimeUnit

class WebhooksUpdateWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {
    override suspend fun doWork(): Result {
        val gatewaySvc: GatewayService = get()
        val webhookSvc: WebHooksService = get()

        try {
            val webhooks = gatewaySvc.getWebHooks().map { it.toDTO() }
            webhookSvc.sync(EntitySource.Cloud, webhooks)
        } catch (th: Throwable) {
            th.printStackTrace()
            return Result.retry()
        }
        return Result.success()
    }

    private fun GatewayApi.WebHook.toDTO(): WebHookDTO {
        return WebHookDTO(
            id = id,
            url = url,
            event = event,
        )
    }

    companion object {
        private const val NAME = "CloudUpdateWorker"

        fun start(context: Context) {
            val work = PeriodicWorkRequestBuilder<WebhooksUpdateWorker>(
                24,
                TimeUnit.HOURS
            )
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
                .enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    work
                )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(NAME)
        }
    }
}