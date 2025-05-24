package me.capcom.smsgateway.modules.localserver

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class LocalServerSettings(
    private val storage: KeyValueStorage,
) : Exporter, Importer {
    var enabled: Boolean
        get() = storage.get<Boolean>(ENABLED) ?: false
        set(value) = storage.set(ENABLED, value)

    var deviceId: String?
        get() = storage.get<String?>(DEVICE_ID)
        set(value) = storage.set(DEVICE_ID, value)

    val port: Int
        get() = storage.get<Int>(PORT) ?: 8080

    val username: String
        get() = storage.get<String?>(USERNAME)
            ?: "sms"
    val password: String
        get() = storage.get<String?>(PASSWORD)
            ?: NanoIdUtils.randomNanoId(
                NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                NanoIdUtils.DEFAULT_ALPHABET,
                8
            ).also { storage.set(PASSWORD, it) }

    companion object {
        private const val ENABLED = "ENABLED"

        private const val DEVICE_ID = "DEVICE_ID"
        private const val PORT = "PORT"
        private const val USERNAME = "USERNAME"
        private const val PASSWORD = "PASSWORD"
    }

    override fun export(): Map<String, *> {
        return mapOf(
            PORT to port,
        )
    }

    override fun import(data: Map<String, *>) {
        data.forEach { (key, value) ->
            when (key) {
                PORT -> storage.set(key, value?.toString()?.toFloat()?.toInt()?.toString())
            }
        }
    }
}