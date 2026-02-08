package me.stappmus.messagegateway.modules.webhooks.db

import com.google.gson.annotations.SerializedName

/**
 * Enumeration representing webhook queue processing status.
 */
enum class WebhookStatus(val value: String, val displayName: String) {
    @SerializedName("pending")
    PENDING("pending", "Pending"),

    @SerializedName("processing")
    PROCESSING("processing", "Processing"),

    @SerializedName("completed")
    COMPLETED("completed", "Completed"),

    @SerializedName("failed")
    FAILED("failed", "Failed"),

    @SerializedName("permanently_failed")
    PERMANENTLY_FAILED("permanently_failed", "Permanently Failed");

    companion object {
        fun fromValue(value: String): WebhookStatus? {
            return values().find { it.value == value }
        }

        fun getDefault(): WebhookStatus = PENDING

        fun isProcessing(status: WebhookStatus): Boolean {
            return status == PENDING || status == PROCESSING
        }

        fun isTerminal(status: WebhookStatus): Boolean {
            return status == COMPLETED || status == PERMANENTLY_FAILED
        }
    }
}