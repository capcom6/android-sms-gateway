package me.capcom.smsgateway.modules.gateway.events

import me.capcom.smsgateway.modules.events.AppEvent

sealed class DeviceRegisteredEvent(
    val server: String,
) : AppEvent(NAME) {
    class Success(
        server: String,
        val login: String,
        val password: String,
    ) : DeviceRegisteredEvent(server)

    class Failure(
        server: String,
        val reason: String,
    ) : DeviceRegisteredEvent(server)

    companion object {
        const val NAME = "DeviceRegisteredEvent"
    }
}