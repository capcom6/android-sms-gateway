package me.capcom.smsgateway.modules.receiver

import me.capcom.smsgateway.helpers.ProviderSubIdMode
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class StateStorage(
    private val storage: KeyValueStorage,
) {
    var mmsLastProcessedID: Long
        get() = storage.get<Long>(MMS_LAST_PROCESSED_ID) ?: 0
        set(value) = storage.set(MMS_LAST_PROCESSED_ID, value)

    var smsLastProcessedID: Long
        get() = storage.get<Long>(SMS_LAST_PROCESSED_ID) ?: 0
        set(value) = storage.set(SMS_LAST_PROCESSED_ID, value)

    /**
     * How this device's SMS provider writes `sub_id`, learned from a message seen on both the
     * broadcast and the inbox observer; [ProviderSubIdMode.UNKNOWN] until learned.
     */
    var providerSubIdMode: ProviderSubIdMode
        get() = storage.get<String>(PROVIDER_SUB_ID_MODE)
            ?.let { name -> ProviderSubIdMode.values().find { it.name == name } }
            ?: ProviderSubIdMode.UNKNOWN
        set(value) = storage.set(PROVIDER_SUB_ID_MODE, value.name)

    companion object {
        private val PREFIX = "state."

        private val MMS_LAST_PROCESSED_ID = PREFIX + "last_processed_id"
        private val SMS_LAST_PROCESSED_ID = PREFIX + "sms_last_processed_id"
        private val PROVIDER_SUB_ID_MODE = PREFIX + "provider_sub_id_mode"
    }
}
