package me.stappmus.messagegateway.modules.webhooks.domain

import com.google.gson.annotations.SerializedName

enum class WebHookEvent(val value: String) {
    @SerializedName("sms:received")
    SmsReceived("sms:received"),

    @SerializedName("sms:sent")
    SmsSent("sms:sent"),

    @SerializedName("sms:delivered")
    SmsDelivered("sms:delivered"),

    @SerializedName("sms:failed")
    SmsFailed("sms:failed"),

    @SerializedName("system:ping")
    SystemPing("system:ping"),

    @SerializedName("sms:data-received")
    SmsDataReceived("sms:data-received"),

    @SerializedName("mms:received")
    MmsReceived("mms:received"),
}