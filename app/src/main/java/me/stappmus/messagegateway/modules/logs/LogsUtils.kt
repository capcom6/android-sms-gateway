package me.stappmus.messagegateway.modules.logs

import android.content.Intent
import org.json.JSONObject

object LogsUtils {
    fun Intent.toLogContext(): Map<String, *> = mapOf(
        "action" to this.action,
        "data" to this.dataString,
        "extras" to JSONObject().apply {
            extras?.keySet()?.forEach { key -> this.putOpt(key, JSONObject.wrap(extras?.get(key))) }
        },
    )

    fun Throwable.toLogContext(): Map<String, *> = mapOf(
        "message" to this.message,
        "stackTrace" to this.stackTrace.take(10).joinToString("\n"),
        "threadName" to Thread.currentThread().name,
    )
}