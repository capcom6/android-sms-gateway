package me.capcom.smsgateway.domain

import com.google.gson.annotations.SerializedName

enum class WebhookDelivery {
    @SerializedName("Disabled")
    Disabled,

    @SerializedName("Individual")
    Individual,

    @SerializedName("Batch")
    Batch,
}