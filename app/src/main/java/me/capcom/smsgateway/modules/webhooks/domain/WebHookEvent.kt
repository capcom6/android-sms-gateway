package me.capcom.smsgateway.modules.webhooks.domain

import com.google.gson.annotations.SerializedName

enum class WebHookEvent(val value: String) {
    @SerializedName("sms:received")
    SmsReceived("sms:received"),

    @SerializedName("sms:batch:received")
    SmsBatchReceived("sms:batch:received"),

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

    @SerializedName("sms:batch:data-received")
    SmsBatchDataReceived("sms:batch:data-received"),

    @SerializedName("mms:received")
    MmsReceived("mms:received"),

    @SerializedName("mms:batch:received")
    MmsBatchReceived("mms:batch:received"),

    @SerializedName("mms:downloaded")
    MmsDownloaded("mms:downloaded"),

    @SerializedName("mms:batch:downloaded")
    MmsBatchDownloaded("mms:batch:downloaded"),

    @SerializedName("app:started")
    AppStarted("app:started"),

    @SerializedName("sms:cancelled")
    SmsCancelled("sms:cancelled"),
}
