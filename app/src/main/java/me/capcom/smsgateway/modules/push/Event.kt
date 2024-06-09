package me.capcom.smsgateway.modules.push

import com.google.gson.annotations.SerializedName

enum class Event {
    @SerializedName("message_enqueued")
    MessageEnqueued,

    @SerializedName("webhooks_updated")
    WebhooksUpdated,
}