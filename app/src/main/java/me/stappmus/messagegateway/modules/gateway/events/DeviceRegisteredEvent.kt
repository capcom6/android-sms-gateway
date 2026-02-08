package me.stappmus.messagegateway.modules.gateway.events

import me.stappmus.messagegateway.modules.events.AppEvent

sealed class DeviceRegisteredEvent(
    val server: String,
) : AppEvent(NAME) {
    class Success(
        server: String,
        val login: String,
        val password: String?,
    ) : DeviceRegisteredEvent(server)

    class Failure(
        server: String,
        val reason: String,
    ) : DeviceRegisteredEvent(server)

    companion object {
        const val NAME = "DeviceRegisteredEvent"
    }
}