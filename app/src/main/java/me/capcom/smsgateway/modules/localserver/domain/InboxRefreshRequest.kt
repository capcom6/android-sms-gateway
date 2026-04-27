package me.capcom.smsgateway.modules.localserver.domain

import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import java.util.Date

data class InboxRefreshRequest(
    val since: Date,
    val until: Date,
    val messageTypes: Set<IncomingMessageType>? = null,
    val triggerWebhooks: Boolean? = null,
) {
    val period: Pair<Date, Date>
        get() = since to until

    fun validate(): InboxRefreshRequest {
        if (since == null) {
            throw IllegalArgumentException("since is required")
        }

        if (until == null) {
            throw IllegalArgumentException("until is required")
        }

        if (since.after(until)) {
            throw IllegalArgumentException("since must be before until")
        }

        val allowed = setOf(IncomingMessageType.SMS, IncomingMessageType.MMS)
        if (messageTypes != null && messageTypes.isEmpty()) {
            throw IllegalArgumentException("messageTypes must not be empty")
        }

        val invalidTypes = (messageTypes ?: emptySet()) - allowed
        if (invalidTypes.isNotEmpty()) {
            throw IllegalArgumentException(
                "messageTypes contains unsupported values: ${invalidTypes.joinToString(",")}"
            )
        }
        return this
    }
}
