package me.capcom.smsgateway.modules.localserver

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class LocalServerSettings(
    private val storage: KeyValueStorage,
) {
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


    val jwtSecret: String
        get() = storage.get<String?>(JWT_SECRET)
            ?: NanoIdUtils.randomNanoId(
                NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                NanoIdUtils.DEFAULT_ALPHABET,
                48
            ).also { storage.set(JWT_SECRET, it) }

    var jwtTtlSeconds: Long
        get() = storage.get<Long>(JWT_TTL_SECONDS) ?: (24L * 60L * 60L)
        set(value) = storage.set(JWT_TTL_SECONDS, value)

    fun regenerateJwtSecret(): String {
        return NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            48
        ).also { storage.set(JWT_SECRET, it) }
    }

    companion object {
        private const val ENABLED = "ENABLED"

        private const val DEVICE_ID = "DEVICE_ID"
        private const val PORT = "PORT"
        private const val USERNAME = "USERNAME"
        private const val PASSWORD = "PASSWORD"
        private const val JWT_SECRET = "JWT_SECRET"
        private const val JWT_TTL_SECONDS = "JWT_TTL_SECONDS"
    }
}