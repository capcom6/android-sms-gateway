package me.capcom.smsgateway.modules.webhooks.domain

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

    @SerializedName("mms:downloaded")
    MmsDownloaded("mms:downloaded"),

    @SerializedName("app:started")
    AppStarted("app:started"),

    @SerializedName("sms:cancelled")
    SmsCancelled("sms:cancelled"),

    /**
     * An outgoing SMS composed on the device itself (e.g. in the default
     * messaging app), as opposed to [SmsSent], which reports messages the
     * gateway sent through its own API.
     */
    @SerializedName("sms:device-sent")
    SmsDeviceSent("sms:device-sent"),
}
