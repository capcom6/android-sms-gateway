package me.capcom.smsgateway.modules.localserver.events

import me.capcom.smsgateway.modules.events.AppEvent

class IPReceivedEvent(
    val localIP: String,
    val publicIP: String,
): AppEvent(NAME) {
    companion object {
        const val NAME = "IPReceivedEvent"
    }
}