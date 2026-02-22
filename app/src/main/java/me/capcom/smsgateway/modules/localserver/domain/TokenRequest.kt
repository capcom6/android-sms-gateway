package me.capcom.smsgateway.modules.localserver.domain

import com.google.gson.annotations.SerializedName

data class TokenRequest(
    @SerializedName("ttl")
    val ttl: Long?,
    @SerializedName("scopes")
    val scopes: List<String>?,
)
