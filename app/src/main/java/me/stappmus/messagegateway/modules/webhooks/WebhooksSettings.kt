package me.stappmus.messagegateway.modules.webhooks

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.stappmus.messagegateway.modules.settings.Exporter
import me.stappmus.messagegateway.modules.settings.Importer
import me.stappmus.messagegateway.modules.settings.KeyValueStorage
import me.stappmus.messagegateway.modules.settings.get

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

    override fun import(data: Map<String, *>): Boolean {
        return data.map { (key, value) ->
            when (key) {
                INTERNET_REQUIRED -> {
                    val newValue = value?.toString()?.toBoolean()
                    val changed = this.internetRequired != (newValue ?: true)

                    storage.set(key, newValue)

                    changed
                }

                RETRY_COUNT -> {
                    val retryCount = value?.toString()?.toFloat()?.toInt() ?: 15
                    if (retryCount < 1) {
                        throw IllegalArgumentException("Retry count must be >= 1")
                    }

                    val changed = this.retryCount != retryCount

                    storage.set(key, retryCount.toString())

                    changed
                }

                SIGNING_KEY -> {
                    val newValue = value?.toString()
                    val changed = this.signingKey != newValue

                    storage.set(key, newValue)

                    changed
                }

                else -> false
            }
        }.any { it }
    }
}
