package me.capcom.smsgateway.modules.events

import com.google.gson.annotations.SerializedName

enum class ExternalEventType {
    @SerializedName("MessageEnqueued")
    MessageEnqueued,

    @SerializedName("WebhooksUpdated")
    WebhooksUpdated,

    @SerializedName("MessagesExportRequested")
    MessagesExportRequested,

    @SerializedName("SettingsUpdated")
    SettingsUpdated,
}