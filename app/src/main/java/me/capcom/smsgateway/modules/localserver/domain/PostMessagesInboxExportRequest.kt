package me.capcom.smsgateway.modules.localserver.domain

import java.util.Date

data class PostMessagesInboxExportRequest(
    val since: Date,
    val until: Date,
) {
    val period: Pair<Date, Date>
        get() = since to until

    fun validate(): PostMessagesInboxExportRequest {
        if (since == null) {
            throw IllegalArgumentException("since is required")
        }

        if (until == null) {
            throw IllegalArgumentException("until is required")
        }

        if (since.after(until)) {
            throw IllegalArgumentException("since must be before until")
        }
        return this
    }
}