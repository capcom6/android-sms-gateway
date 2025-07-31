package me.capcom.smsgateway.modules.receiver.events

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.events.AppEvent
import java.util.Date

class MessagesExportRequestedEvent(
    val since: Date,
    val until: Date,
) : AppEvent(NAME) {
    data class Payload(
        @SerializedName("since")
        val since: Date,
        @SerializedName("until")
        val until: Date,
    )

    companion object {
        const val NAME = "MessagesExportRequestedEvent"

        fun withPayload(payload: String): MessagesExportRequestedEvent {
            val obj = GsonBuilder().configure().create().fromJson(payload, Payload::class.java)
            return MessagesExportRequestedEvent(obj.since, obj.until)
        }
    }
}