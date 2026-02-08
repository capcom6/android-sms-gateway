package me.stappmus.messagegateway.modules.gateway

import me.stappmus.messagegateway.modules.settings.Exporter
import me.stappmus.messagegateway.modules.settings.Importer
import me.stappmus.messagegateway.modules.settings.KeyValueStorage
import me.stappmus.messagegateway.modules.settings.get

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

    var fcmToken: String?
        get() = storage.get(FCM_TOKEN)
        set(value) = storage.set(FCM_TOKEN, value)

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
        private const val FCM_TOKEN = "fcm_token"

        private const val CLOUD_URL = "cloud_url"
        private const val PRIVATE_TOKEN = "private_token"

        // Upstream cloud server removed — set your own server URL via settings
        const val PUBLIC_URL = ""
    }

    override fun export(): Map<String, *> {
        return mapOf(
            CLOUD_URL to serverUrl,
        )
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map {
            when (it.key) {
                CLOUD_URL -> {
                    val url = it.value?.toString() ?: PUBLIC_URL
                    if (url.isNotEmpty() && !url.startsWith("https://")) {
                        throw IllegalArgumentException("url must start with https://")
                    }

                    val changed = serverUrl != url

                    storage.set(it.key, url)

                    changed
                }

                PRIVATE_TOKEN -> {
                    val newValue = it.value?.toString()
                    val changed = privateToken != newValue

                    storage.set(it.key, newValue)

                    changed
                }

                else -> false
            }
        }.any { it }
    }
}
