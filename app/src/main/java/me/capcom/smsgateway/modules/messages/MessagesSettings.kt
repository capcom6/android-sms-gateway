package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class MessagesSettings(
    private val storage: KeyValueStorage,
) {

    val secondsBetweenMessages: Int
        get() = storage.get<Int>(SECONDS_BETWEEN_MESSAGES) ?: 0

    companion object {
        private const val SECONDS_BETWEEN_MESSAGES = "SECONDS_BETWEEN_MESSAGES"
    }
}