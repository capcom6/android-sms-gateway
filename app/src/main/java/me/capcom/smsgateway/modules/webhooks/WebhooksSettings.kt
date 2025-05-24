package me.capcom.smsgateway.modules.webhooks

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class WebhooksSettings(
    private val storage: KeyValueStorage,
) : Exporter, Importer {
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

    override fun export(): Map<String, *> {
        return mapOf(
            INTERNET_REQUIRED to internetRequired,
            RETRY_COUNT to retryCount,
        )
    }

    override fun import(data: Map<String, *>) {
        data.forEach { (key, value) ->
            when (key) {
                INTERNET_REQUIRED -> storage.set(key, value?.toString()?.toBoolean())
                RETRY_COUNT -> {
                    val retryCount = value?.toString()?.toInt()
                    if (retryCount != null && retryCount < 1) {
                        throw IllegalArgumentException("Retry count must be >= 1")
                    }
                    storage.set(key, retryCount?.toString())
                }

                SIGNING_KEY -> storage.set(key, value?.toString())
            }
        }
    }
}