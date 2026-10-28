package me.capcom.smsgateway.modules.receiver

import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class ReceiverSettings(
    private val storage: KeyValueStorage,
) : Exporter, Importer {
    var contentProviderEnabled: Boolean
        get() = storage.get<Boolean>(CONTENT_PROVIDER_ENABLED) ?: true
        set(value) = storage.set(CONTENT_PROVIDER_ENABLED, value)

    /**
     * Maximum size of an MMS attachment that may be inlined (base64) into
     * webhook payloads and cloud uploads. Larger parts are kept on the device
     * only and are served by the inbox attachments endpoint.
     *
     * `0` disables inlining completely. The value is stored as a string, so an
     * unparsable value falls back to the default instead of being silently
     * treated as `0`.
     */
    var mmsMaxInlineAttachmentBytes: Long
        get() = storage.get<String>(MMS_MAX_INLINE_ATTACHMENT_BYTES)
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it in 0..MAX_INLINE_ATTACHMENT_BYTES }
            ?: DEFAULT_MAX_INLINE_ATTACHMENT_BYTES
        set(value) = storage.set(MMS_MAX_INLINE_ATTACHMENT_BYTES, value.toString())

    override fun export(): Map<String, *> {
        return mapOf(
            CONTENT_PROVIDER_ENABLED to contentProviderEnabled,
            MMS_MAX_INLINE_ATTACHMENT_BYTES to mmsMaxInlineAttachmentBytes,
        )
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map { (key, value) ->
            when (key) {
                CONTENT_PROVIDER_ENABLED -> {
                    val enabled = value?.toString()?.toBooleanStrictOrNull()
                        ?: throw IllegalArgumentException("$CONTENT_PROVIDER_ENABLED must be a boolean")
                    val changed = contentProviderEnabled != enabled
                    storage.set(key, enabled)
                    changed
                }

                MMS_MAX_INLINE_ATTACHMENT_BYTES -> {
                    val bytes = value.asLongOrNull()
                        ?: throw IllegalArgumentException("$MMS_MAX_INLINE_ATTACHMENT_BYTES must be a number")
                    if (bytes !in 0..MAX_INLINE_ATTACHMENT_BYTES) {
                        throw IllegalArgumentException(
                            "$MMS_MAX_INLINE_ATTACHMENT_BYTES must be between 0 and $MAX_INLINE_ATTACHMENT_BYTES"
                        )
                    }
                    val changed = mmsMaxInlineAttachmentBytes != bytes
                    storage.set(key, bytes.toString())
                    changed
                }

                else -> false
            }
        }.any { it }
    }

    /**
     * Parses a whole-number value from an API payload. Gson deserializes JSON
     * numbers into [Double], so `1048576` arrives as `1048576.0` and must not
     * be rejected by a `Long`-only parse.
     */
    private fun Any?.asLongOrNull(): Long? {
        val value = this ?: return null
        if (value is Number) {
            val asDouble = value.toDouble()
            if (!asDouble.isFinite()) return null
            val asLong = asDouble.toLong()
            return asLong.takeIf { it.toDouble() == asDouble }
        }
        return value.toString().trim().toLongOrNull()
    }

    companion object {
        private const val CONTENT_PROVIDER_ENABLED = "content_provider_enabled"
        private const val MMS_MAX_INLINE_ATTACHMENT_BYTES = "mms_max_inline_attachment_bytes"

        const val DEFAULT_MAX_INLINE_ATTACHMENT_BYTES = 1_048_576L
        const val MAX_INLINE_ATTACHMENT_BYTES = 67_108_864L
    }
}
