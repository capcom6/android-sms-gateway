package me.capcom.smsgateway.modules.localserver.domain

import com.google.gson.annotations.SerializedName
import java.util.Date

data class PostMessageRequest(
    val id: String?,
    val message: String,
    val phoneNumbers: List<String>,
    val simNumber: Int?,
    val withDeliveryReport: Boolean?,
    val isEncrypted: Boolean?,

    @SerializedName("ttl")
    private val _ttl: Long?,
    @SerializedName("validUntil")
    private val _validUntil: Date?
) {
    val validUntil: Date?
        get() {
            if (_ttl != null && _validUntil != null) {
                throw IllegalArgumentException("fields conflict: ttl and validUntil")
            }

            val validUntil = _validUntil
                ?: _ttl?.let { Date(System.currentTimeMillis() + (it * 1000L)) }

            if (validUntil?.before(Date()) == true) {
                throw IllegalArgumentException("message already expired")
            }

            return validUntil
        }
}