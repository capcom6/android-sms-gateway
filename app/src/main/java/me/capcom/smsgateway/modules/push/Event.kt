package me.capcom.smsgateway.modules.push

import com.google.gson.annotations.SerializedName

enum class Event {
    @SerializedName("MessageEnqueued")
    MessageEnqueued,

    @SerializedName("WebhooksUpdated")
    WebhooksUpdated,

    @SerializedName("MessagesExportRequested")
    MessagesExportRequested,

    @SerializedName("SettingsUpdated")
    SettingsUpdated,
}