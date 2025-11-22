package me.capcom.smsgateway.modules.webhooks.db

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room type converters for webhook queue entities.
 */
class WebhookConverters {
    
    @TypeConverter
    fun fromPriority(priority: WebhookPriority): Int {
        return priority.value
    }
    
    @TypeConverter
    fun toPriority(value: Int): WebhookPriority {
        return WebhookPriority.fromValue(value) ?: WebhookPriority.NORMAL
    }
    
    @TypeConverter
    fun fromStatus(status: WebhookStatus): String {
        return status.value
    }
    
    @TypeConverter
    fun toStatus(value: String): WebhookStatus {
        return WebhookStatus.fromValue(value) ?: WebhookStatus.PENDING
    }
    
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
}