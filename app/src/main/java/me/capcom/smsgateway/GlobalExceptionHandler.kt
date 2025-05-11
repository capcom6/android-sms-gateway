package me.capcom.smsgateway

import android.util.Log
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import org.koin.core.component.KoinComponent
import java.lang.Thread.UncaughtExceptionHandler

class GlobalExceptionHandler(
    private val defaultHandler: UncaughtExceptionHandler,
    private val logger: LogsService
) : UncaughtExceptionHandler, KoinComponent {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            logger.insert(
                LogEntry.Priority.ERROR,
                "GlobalExceptionHandler",
                "Unhandled exception in ${thread.name}",
                mapOf(
                    "message" to throwable.message,
                    "stackTrace" to throwable.stackTrace.joinToString("\n"),
                    "threadName" to thread.name,
                )
            )
        } catch (e: Exception) {
            Log.e("GlobalExceptionHandler", "Failed to log uncaught exception", e)
        } finally {
            defaultHandler.uncaughtException(thread, throwable)
        }
    }
}
