package me.capcom.smsgateway.modules.webhooks

import android.content.Context
import java.io.File

class WebhookPayloadStorage(
    context: Context,
) {
    private val payloadDir = File(context.filesDir, PAYLOAD_DIR_NAME).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    fun save(key: String, payload: String): String {
        val file = File(payloadDir, key)
        file.writeText(payload)
        return FILE_REF_PREFIX + key
    }

    fun read(payloadRefOrInlinePayload: String): String? {
        if (!payloadRefOrInlinePayload.startsWith(FILE_REF_PREFIX)) {
            // Backward compatibility for queue rows that still store inline payload.
            return payloadRefOrInlinePayload
        }

        val key = payloadRefOrInlinePayload.removePrefix(FILE_REF_PREFIX)
        val file = File(payloadDir, key)
        if (!file.exists()) {
            return null
        }

        return file.readText()
    }

    fun delete(key: String) {
        val file = File(payloadDir, key)
        if (file.exists()) {
            file.delete()
        }
    }

    companion object {
        private const val PAYLOAD_DIR_NAME = "webhook_payloads"
        private const val FILE_REF_PREFIX = "file:"
    }
}
