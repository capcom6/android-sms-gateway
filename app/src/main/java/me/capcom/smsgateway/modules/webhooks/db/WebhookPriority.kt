package me.capcom.smsgateway.modules.webhooks.db

import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.annotations.SerializedName

/**
 * Enumeration representing webhook processing priority levels.
 * Higher priority values are processed first.
 */
enum class WebhookPriority(val value: Int, val displayName: String) {
    @SerializedName("0")
    LOW(0, "Low"),
    
    @SerializedName("5")
    NORMAL(5, "Normal"),
    
    @SerializedName("8")
    HIGH(8, "High"),
    
    @SerializedName("10")
    CRITICAL(10, "Critical");
    
    companion object {
        fun fromValue(value: Int): WebhookPriority? {
            return values().find { it.value == value }
        }
        
        fun getDefault(): WebhookPriority = NORMAL
    }
}