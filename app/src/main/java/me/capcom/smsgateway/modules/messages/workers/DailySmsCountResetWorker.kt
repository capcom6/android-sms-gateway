package me.capcom.smsgateway.modules.messages.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.capcom.smsgateway.data.dao.AgentPhoneDao // Changed
import me.capcom.smsgateway.modules.localsms.utils.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class DailySmsCountResetWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val agentPhoneDao: AgentPhoneDao by inject() // Changed
    private val logger = Logger.get(this.javaClass.simpleName)

    override suspend fun doWork(): Result {
        logger.info("Starting daily SMS count reset for AgentPhones.")
        try {
            val resetTime = System.currentTimeMillis()
            agentPhoneDao.resetAllCounts(resetTime)
            logger.info("Successfully called resetAllCounts for AgentPhones with resetTime: $resetTime.")
            return Result.success()
        } catch (e: Exception) {
            logger.error("Error during daily SMS count reset process for AgentPhones: ${e.message}", e)
            return Result.retry() // Retry on failure
        }
    }

    companion object {
        private const val WORK_NAME = "DailySmsCountResetWorker"

        fun start(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresCharging(false)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DailySmsCountResetWorker>(
                1, TimeUnit.DAYS // Repeat once per day
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if it's already scheduled
                workRequest
            )
            Logger.get(WORK_NAME).info("DailySmsCountResetWorker scheduled.")
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Logger.get(WORK_NAME).info("DailySmsCountResetWorker stopped/cancelled.")
        }
    }
}
