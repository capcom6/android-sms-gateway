package me.capcom.smsgateway.modules.gateway

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class GatewaySettings(
    private val storage: KeyValueStorage,
) {

    var enabled: Boolean
        get() = storage.get<Boolean>(ENABLED) ?: false
        set(value) = storage.set(ENABLED, value)

    // TODO: save per server/invalidate on server change/invalidate on unauthorized error
    var registrationInfo: GatewayApi.DeviceRegisterResponse?
        get() = storage.get(REGISTRATION_INFO)
        set(value) = storage.set(REGISTRATION_INFO, value)

    val privateUrl: String?
        get() = storage.get<String?>(PRIVATE_URL)
    val privateToken: String?
        get() = storage.get<String>(PRIVATE_TOKEN)

    companion object {
        private const val REGISTRATION_INFO = "REGISTRATION_INFO"
        private const val ENABLED = "ENABLED"

        private const val PRIVATE_URL = "private_url"
        private const val PRIVATE_TOKEN = "private_token"
    }
}