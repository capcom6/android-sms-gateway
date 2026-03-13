package me.capcom.smsgateway.modules.receiver

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class StateStorage(
    private val storage: KeyValueStorage,
) {
    var mmsLastProcessedID: Long
        get() = storage.get<Long>(MMS_LAST_PROCESSED_ID) ?: 0
        set(value) = storage.set(MMS_LAST_PROCESSED_ID, value)

    companion object {
        private val PREFIX = "state."

        private val MMS_LAST_PROCESSED_ID = PREFIX + "last_processed_id"
    }
}