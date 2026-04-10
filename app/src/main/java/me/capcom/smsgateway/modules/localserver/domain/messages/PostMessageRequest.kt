package me.capcom.smsgateway.modules.localserver.domain.messages

import com.google.gson.annotations.SerializedName
import java.util.Date


data class PostMessageRequest(
    val id: String?,
    @Deprecated("Use textMessage instead")
    val message: String?,
    val phoneNumbers: List<String>,
    val simNumber: Int?,
    val withDeliveryReport: Boolean?,
    val isEncrypted: Boolean?,
    val priority: Byte = 0,

    val textMessage: TextMessage? = null,
    val dataMessage: DataMessage? = null,

    val deviceId: String? = null,

    @SerializedName("ttl")
    private val _ttl: Long?,
    @SerializedName("validUntil")
    private val _validUntil: Date?,
    val scheduleAt: Date? = null,
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

    fun validate(): PostMessageRequest {
        val messageTypes =
            listOfNotNull(textMessage, dataMessage, message)
        when {
            messageTypes.isEmpty() -> throw IllegalArgumentException("Must specify exactly one of: textMessage, dataMessage, or message")
            messageTypes.size > 1 -> throw IllegalArgumentException("Cannot specify multiple message types simultaneously")
        }

        // Validate message parameters
        if (message?.isEmpty() == true) {
            throw IllegalArgumentException("Text message is empty")
        }

        // Validate data message parameters
        dataMessage?.let { dataMsg ->
            // Port validation
            if (dataMsg.port < 0 || dataMsg.port > 65535) {
                throw IllegalArgumentException("Port must be between 0 and 65535")
            }

            // Data validation (only for non-empty check)
            if (dataMsg.data.isEmpty()) {
                throw IllegalArgumentException("Data message cannot be empty")
            }
        }

        // Validate text message parameters
        if (textMessage?.text?.isEmpty() == true) {
            throw IllegalArgumentException("Text message is empty")
        }

        if (phoneNumbers.isEmpty()) {
            throw IllegalArgumentException("Empty phone numbers list")
        }

        if (simNumber != null && simNumber < 1) {
            throw IllegalArgumentException("SIM number cannot be less than 1")
        }

        if (scheduleAt?.after(Date()) == false) {
            throw IllegalArgumentException("scheduleAt must be in the future")
        }

        return this
    }
}
