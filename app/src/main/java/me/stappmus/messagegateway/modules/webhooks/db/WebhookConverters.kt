package me.stappmus.messagegateway.modules.webhooks.db

import androidx.room.TypeConverter

/**
 * Room type converters for webhook queue entities.
 */
class WebhookConverters {
    @TypeConverter
    fun fromStatus(status: WebhookStatus): String {
        return status.value
    }

    @TypeConverter
    fun toStatus(value: String): WebhookStatus {
        return WebhookStatus.fromValue(value) ?: WebhookStatus.PENDING
    }
}