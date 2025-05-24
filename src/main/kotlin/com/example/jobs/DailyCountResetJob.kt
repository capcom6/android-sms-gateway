package com.example.jobs

import com.example.services.AgentService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DailyCountResetJob(private val agentService: AgentService) {
    private val logger = LoggerFactory.getLogger(DailyCountResetJob::class.java)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    suspend fun runReset() {
        logger.info("Executing daily agent SMS count reset.")
        try {
            val updatedRows = agentService.resetAllCounts()
            logger.info("Daily agent SMS count reset complete. $updatedRows agents updated.")
        } catch (e: Exception) {
            logger.error("Error during daily agent SMS count reset: ${e.message}", e)
        }
    }

    fun schedule(initialDelayHours: Long = 1, periodHours: Long = 24) {
        // Calculate delay until next midnight or a specific time if needed.
        // For MVP, a simple initial delay and period is fine.
        // Example: Run at 1 AM daily, assuming server starts at various times.
        // This is a simplified scheduling. A more robust scheduler (e.g., Quartz) might be used in a full app.
        
        // For immediate testing, one might use a shorter initial delay and period.
        // For production, ensure this aligns with desired reset time (e.g., midnight UTC).
        
        scheduler.scheduleAtFixedRate({
            logger.info("Scheduled daily reset job is triggered.")
            runBlocking { // Bridge to coroutine world from scheduled executor
                runReset()
            }
        }, initialDelayHours, periodHours, TimeUnit.HOURS)
        logger.info("DailyCountResetJob scheduled to run every $periodHours hours, starting in $initialDelayHours hour(s).")
    }

    fun stop() {
        logger.info("Stopping DailyCountResetJob scheduler.")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (ie: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
        logger.info("DailyCountResetJob scheduler stopped.")
    }
}
