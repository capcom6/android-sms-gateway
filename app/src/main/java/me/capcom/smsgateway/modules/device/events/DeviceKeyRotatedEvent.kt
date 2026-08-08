package me.capcom.smsgateway.modules.device.events

import me.capcom.smsgateway.modules.events.AppEvent

class DeviceKeyRotatedEvent : AppEvent(NAME) {
    companion object {
        const val NAME = "DeviceKeyRotatedEvent"
    }
}
