package me.capcom.smsgateway.modules.push.payloads

import com.google.gson.GsonBuilder
import me.capcom.smsgateway.extensions.configure
import java.util.Date

data class MessagesExportRequestedPayload(
    val since: Date,
    val until: Date,
) {
    companion object {
        fun from(json: String): MessagesExportRequestedPayload {
            val gson = GsonBuilder().configure().create()
            return gson.fromJson(json, MessagesExportRequestedPayload::class.java)
        }
    }
}
