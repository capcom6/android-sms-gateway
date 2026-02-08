package me.stappmus.messagegateway.modules.receiver.events

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import me.stappmus.messagegateway.extensions.configure
import me.stappmus.messagegateway.modules.events.AppEvent
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