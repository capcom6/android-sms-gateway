package me.capcom.smsgateway.modules.gateway.events

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.events.AppEvent

class MessageCancelledEvent(val messageId: String) : AppEvent(NAME) {
    data class Payload(
        @SerializedName("messageId")
        val messageId: String
    )

    companion object {
        fun withPayload(payload: String): MessageCancelledEvent {
            val obj = GsonBuilder().configure().create().fromJson(payload, Payload::class.java)

            return MessageCancelledEvent(
                obj.messageId,
            )
        }

        const val NAME = "MessageCancelledEvent"
    }
}
