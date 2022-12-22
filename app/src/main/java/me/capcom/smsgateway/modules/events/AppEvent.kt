package me.capcom.smsgateway.modules.events

sealed class AppEvent {
    class FcmTokenUpdated(val token: String): AppEvent()
}