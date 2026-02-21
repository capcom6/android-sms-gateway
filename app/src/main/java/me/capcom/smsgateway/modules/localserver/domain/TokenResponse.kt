package me.capcom.smsgateway.modules.localserver.domain

import com.google.gson.annotations.SerializedName
import java.util.Date

data class TokenResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("expires_at")
    val expiresAt: Date,
)
