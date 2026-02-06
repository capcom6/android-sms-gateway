package me.capcom.smsgateway.modules.media

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class MediaSettings(
    private val storage: KeyValueStorage,
) : Exporter, Importer {
    val retentionDays: Int
        get() = storage.get<Int>(RETENTION_DAYS) ?: 7

    val tokenTtlSeconds: Int
        get() = storage.get<Int>(TOKEN_TTL_SECONDS) ?: 900

    val maxAttachmentSizeMb: Int
        get() = storage.get<Int>(MAX_ATTACHMENT_SIZE_MB) ?: 20

    val signingKey: String
        get() = storage.get<String>(SIGNING_KEY)
            ?: NanoIdUtils.randomNanoId(
                NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                NanoIdUtils.DEFAULT_ALPHABET,
                32
            ).also {
                storage.set(SIGNING_KEY, it)
            }

    companion object {
        const val RETENTION_DAYS = "retention_days"
        const val TOKEN_TTL_SECONDS = "token_ttl_seconds"
        const val MAX_ATTACHMENT_SIZE_MB = "max_attachment_size_mb"
        const val SIGNING_KEY = "signing_key"
    }

    override fun export(): Map<String, *> {
        return mapOf(
            RETENTION_DAYS to retentionDays,
            TOKEN_TTL_SECONDS to tokenTtlSeconds,
            MAX_ATTACHMENT_SIZE_MB to maxAttachmentSizeMb,
        )
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map { (key, value) ->
            when (key) {
                RETENTION_DAYS -> {
                    val parsed = value?.toString()?.toFloat()?.toInt() ?: 7
                    if (parsed < 1) {
                        throw IllegalArgumentException("Retention days must be >= 1")
                    }

                    val changed = retentionDays != parsed
                    storage.set(key, parsed)
                    changed
                }

                TOKEN_TTL_SECONDS -> {
                    val parsed = value?.toString()?.toFloat()?.toInt() ?: 900
                    if (parsed < 60) {
                        throw IllegalArgumentException("Token ttl seconds must be >= 60")
                    }

                    val changed = tokenTtlSeconds != parsed
                    storage.set(key, parsed)
                    changed
                }

                MAX_ATTACHMENT_SIZE_MB -> {
                    val parsed = value?.toString()?.toFloat()?.toInt() ?: 20
                    if (parsed < 1) {
                        throw IllegalArgumentException("Max attachment size must be >= 1 MB")
                    }

                    val changed = maxAttachmentSizeMb != parsed
                    storage.set(key, parsed)
                    changed
                }

                SIGNING_KEY -> {
                    val parsed = value?.toString()?.trim()
                    if (parsed.isNullOrBlank()) {
                        throw IllegalArgumentException("Signing key cannot be blank")
                    }

                    val changed = signingKey != parsed
                    storage.set(key, parsed)
                    changed
                }

                else -> false
            }
        }.any { it }
    }
}
