package me.capcom.smsgateway.modules.webhooks.domain

import com.google.gson.annotations.SerializedName

enum class WebHookEvent {
    @SerializedName("sms:received")
    SmsReceived,

    @SerializedName("sms:sent")
    SmsSent,

    @SerializedName("system:ping")
    SystemPing,
}