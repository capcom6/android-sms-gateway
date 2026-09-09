package me.capcom.smsgateway.modules.gateway.inbox

import com.google.gson.annotations.SerializedName

data class EncryptedAttachment(
    @SerializedName("partId") val partId: Long,
    @SerializedName("contentType") val contentType: String,
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long?,
    @SerializedName("data") val data: String,
)
