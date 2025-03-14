package me.capcom.smsgateway.modules.webhooks

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class TemporaryStorage(
    private val storage: KeyValueStorage,
) {
    fun put(key: String, value: String) {
        storage.set(PREFIX + key, value)
    }

    fun get(key: String): String? {
        return storage.get(PREFIX + key)
    }

    fun remove(key: String) {
        storage.remove(PREFIX + key)
    }

    companion object {
        private val PREFIX = "storage."
    }
}