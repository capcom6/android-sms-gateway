package me.capcom.smsgateway.modules.webhooks

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class WebhooksSettings(
    private val storage: KeyValueStorage,
) {
    val internetRequired: Boolean
        get() = storage.get<Boolean>(INTERNET_REQUIRED) ?: true

    val retryCount: Int
        get() = storage.get<Int>(RETRY_COUNT) ?: 15

    val signingKey: String
        get() = storage.get<String>(SIGNING_KEY)
            ?: NanoIdUtils.randomNanoId(
                NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                NanoIdUtils.DEFAULT_ALPHABET,
                8
            ).also { storage.set(SIGNING_KEY, it) }

    companion object {
        const val INTERNET_REQUIRED = "internet_required"
        const val RETRY_COUNT = "retry_count"
        const val SIGNING_KEY = "signing_key"
    }
}