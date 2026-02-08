package me.stappmus.messagegateway.modules.receiver.events

import me.stappmus.messagegateway.modules.events.AppEvent
import me.stappmus.messagegateway.modules.webhooks.payload.MmsReceivedPayload
import me.stappmus.messagegateway.modules.webhooks.payload.SmsEventPayload

class SmsReceivedEvent(
    val payload: SmsEventPayload.SmsReceived,
) : AppEvent(NAME) {
    companion object {
        const val NAME = "SmsReceivedEvent"
    }
}

class SmsDataReceivedEvent(
    val payload: SmsEventPayload.SmsDataReceived,
) : AppEvent(NAME) {
    companion object {
        const val NAME = "SmsDataReceivedEvent"
    }
}

class MmsReceivedEvent(
    val payload: MmsReceivedPayload,
) : AppEvent(NAME) {
    companion object {
        const val NAME = "MmsReceivedEvent"
    }
}
