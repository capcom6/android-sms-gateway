package me.capcom.smsgateway.modules.webhooks.domain

import com.google.gson.annotations.SerializedName

enum class WebHookEvent {
    @SerializedName("sms:received")
    SmsReceived,

    @SerializedName("sms:sent")
    SmsSent,

    @SerializedName("sms:delivered")
    SmsDelivered,

    @SerializedName("sms:failed")
    SmsFailed,

    @SerializedName("system:ping")
    SystemPing,
}