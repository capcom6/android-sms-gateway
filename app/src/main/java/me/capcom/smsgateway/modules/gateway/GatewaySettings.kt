package me.capcom.smsgateway.modules.gateway

import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class GatewaySettings(
    private val storage: KeyValueStorage,
) : Exporter, Importer {
    var enabled: Boolean
        get() = storage.get<Boolean>(ENABLED) ?: false
        set(value) = storage.set(ENABLED, value)

    val deviceId: String?
        get() = registrationInfo?.id

    var registrationInfo: GatewayApi.DeviceRegisterResponse?
        get() = storage.get(REGISTRATION_INFO)
        set(value) = storage.set(REGISTRATION_INFO, value)

    val username: String?
        get() = registrationInfo?.login
    val password: String?
        get() = registrationInfo?.password

    val serverUrl: String
        get() = storage.get<String?>(CLOUD_URL) ?: PUBLIC_URL
    val privateToken: String?
        get() = storage.get<String>(PRIVATE_TOKEN)

    companion object {
        private const val REGISTRATION_INFO = "REGISTRATION_INFO"
        private const val ENABLED = "ENABLED"

        private const val CLOUD_URL = "cloud_url"
        private const val PRIVATE_TOKEN = "private_token"

        const val PUBLIC_URL = "https://api.sms-gate.app/mobile/v1"
    }

    override fun export(): Map<String, *> {
        return mapOf(
            CLOUD_URL to serverUrl,
        )
    }

    override fun import(data: Map<String, *>) {
        data.forEach { (key, value) ->
            when (key) {
                CLOUD_URL -> {
                    val url = value?.toString()
                    if (url != null && !url.startsWith("https://")) {
                        throw IllegalArgumentException("url must start with https://")
                    }
                    storage.set(key, url)
                }

                PRIVATE_TOKEN -> storage.set(key, value?.toString())
            }
        }
    }
}