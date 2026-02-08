package me.stappmus.messagegateway.modules.localserver.domain

import java.util.Date

data class PostMessagesInboxExportRequest(
    val since: Date,
    val until: Date,
) {
    val period: Pair<Date, Date>
        get() = since to until

    fun validate(): PostMessagesInboxExportRequest {
        if (since.after(until)) {
            throw IllegalArgumentException("since must be before until")
        }
        return this
    }
}
