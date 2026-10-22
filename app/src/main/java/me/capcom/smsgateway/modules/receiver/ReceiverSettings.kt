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

    override fun export(): Map<String, *> {
        return mapOf(
            CONTENT_PROVIDER_ENABLED to contentProviderEnabled,
        )
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map { (key, value) ->
            when (key) {
                CONTENT_PROVIDER_ENABLED -> {
                    val raw = value?.toString()
                    if (raw != null && raw.toBooleanStrictOrNull() == null) {
                        throw IllegalArgumentException("$CONTENT_PROVIDER_ENABLED must be true or false")
                    }
                    val newValue = raw?.toBooleanStrictOrNull()
                    val changed = contentProviderEnabled != (newValue ?: true)

                    storage.set(
                        key,
                        newValue
                    )

                    changed
                }

                else -> false
            }
        }.any { it }
    }

    companion object {
        private const val CONTENT_PROVIDER_ENABLED = "content_provider_enabled"
    }
}
