package me.capcom.smsgateway.modules.receiver.events

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import me.capcom.smsgateway.domain.WebhookDelivery
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.events.AppEvent
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import java.util.Date

class MessagesExportRequestedEvent(
    val since: Date,
    val until: Date,
    val messageTypes: Set<IncomingMessageType>,
    val webhookDelivery: WebhookDelivery?,
) : AppEvent(NAME) {
    data class Payload(
        @SerializedName("since")
        val since: Date,
        @SerializedName("until")
        val until: Date,
        @SerializedName("messageTypes")
        val messageTypes: String? = null,
        @SerializedName("triggerWebhooks")
        @Deprecated("Replaced with webhookDelivery")
        val triggerWebhooks: Boolean? = null,
        @SerializedName("webhookDelivery")
        val webhookDelivery: WebhookDelivery? = null,
    )

    companion object {
        const val NAME = "MessagesExportRequestedEvent"

        fun withPayload(payload: String): MessagesExportRequestedEvent {
            val obj = GsonBuilder().configure().create().fromJson(payload, Payload::class.java)
            val messageTypes = try {
                obj.messageTypes
                    ?.split(',')
                    ?.map { IncomingMessageType.valueOf(it) }
                    ?.toSet()
            } catch (_: Exception) {
                null
            }

            return MessagesExportRequestedEvent(
                since = obj.since,
                until = obj.until,
                messageTypes = messageTypes ?: setOf(IncomingMessageType.SMS),
                webhookDelivery = obj.webhookDelivery
                    ?: (if (obj.triggerWebhooks == false) WebhookDelivery.Disabled else null)
                    ?: WebhookDelivery.Individual,
            )
        }
    }
}
