package me.capcom.smsgateway.modules.receiver.events

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.events.AppEvent
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import java.util.Date

class MessagesExportRequestedEvent(
    val since: Date,
    val until: Date,
    val messageTypes: Set<IncomingMessageType>,
    val triggerWebhooks: Boolean,
) : AppEvent(NAME) {
    data class Payload(
        @SerializedName("since")
        val since: Date,
        @SerializedName("until")
        val until: Date,
        @SerializedName("messageTypes")
        val messageTypes: Set<IncomingMessageType>? = null,
        @SerializedName("triggerWebhooks")
        val triggerWebhooks: Boolean? = null,
    )

    companion object {
        const val NAME = "MessagesExportRequestedEvent"

        fun withPayload(payload: String): MessagesExportRequestedEvent {
            val obj = GsonBuilder().configure().create().fromJson(payload, Payload::class.java)
            return MessagesExportRequestedEvent(
                since = obj.since,
                until = obj.until,
                messageTypes = obj.messageTypes ?: setOf(IncomingMessageType.SMS),
                triggerWebhooks = obj.triggerWebhooks ?: true,
            )
        }
    }
}
