package me.capcom.smsgateway.modules.settings

import android.content.Context
import android.util.Log
import me.capcom.smsgateway.R
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.incoming.IncomingMessagesSettings
import me.capcom.smsgateway.modules.logs.LogsSettings
import me.capcom.smsgateway.modules.messages.MessagesSettings
import me.capcom.smsgateway.modules.notifications.NotificationsService
import me.capcom.smsgateway.modules.ping.PingSettings
import me.capcom.smsgateway.modules.webhooks.WebhooksSettings

class SettingsService(
    private val context: Context,
    private val notificationsService: NotificationsService,
    encryptionSettings: EncryptionSettings,
    gatewaySettings: GatewaySettings,
    incomingMessagesSettings: IncomingMessagesSettings,
    messagesSettings: MessagesSettings,
    pingSettings: PingSettings,
    logsSettings: LogsSettings,
    webhooksSettings: WebhooksSettings
) {
    private val settings = mapOf(
        "encryption" to encryptionSettings,
        "gateway" to gatewaySettings,
        "incoming" to incomingMessagesSettings,
        "messages" to messagesSettings,
        "ping" to pingSettings,
        "logs" to logsSettings,
        "webhooks" to webhooksSettings
    )

    /**
     * Keys the cloud server is not permitted to set.
     *
     * SettingsUpdateWorker applies whatever the server returns, every 24
     * hours. These two values exist specifically so that the server cannot
     * read or forge traffic, so letting the server replace them defeats
     * their entire purpose: swapping the passphrase makes future
     * "end-to-end encrypted" messages readable by it, and swapping the
     * signing key lets it forge events the receiver will trust. The only
     * signal to the user was a generic "settings changed" notification.
     *
     * The authenticated local API (PATCH /settings, settings:write) is
     * unaffected — that is the device owner configuring their own device.
     */
    private fun stripCloudProtected(data: Map<String, *>): Map<String, *> {
        return data.mapValues { (section, value) ->
            val blocked = CLOUD_PROTECTED_KEYS[section] ?: return@mapValues value
            val inner = value as? Map<*, *> ?: return@mapValues value
            val kept = inner.filterKeys { it !in blocked }
            if (kept.size != inner.size) {
                Log.w(
                    "SettingsService",
                    "Ignoring cloud-supplied $section keys: " +
                            inner.keys.filter { it in blocked }.joinToString()
                )
            }
            kept
        }.filterValues { (it as? Map<*, *>)?.isNotEmpty() ?: (it != null) }
    }

    fun getAll(): Map<String, *> {
        return settings.mapValues { (it.value as? Exporter)?.export() }
    }

    companion object {
        private val CLOUD_PROTECTED_KEYS: Map<String, Set<String>> = mapOf(
            "encryption" to setOf("passphrase"),
            "webhooks" to setOf("signing_key"),
        )
    }

    fun update(data: Map<String, *>, source: EntitySource = EntitySource.Local) {
        if (data.isEmpty()) {
            return
        }

        val effective = if (source == EntitySource.Cloud) stripCloudProtected(data) else data
        if (effective.isEmpty()) {
            return
        }

        val changed = effective.map { (key, value) ->
            try {
                settings[key]?.let {
                    (it as? Importer)?.import(value as Map<String, *>)
                }
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Failed to import $key: ${e.message}", e)
            }
        }

        if (changed.none { it == true }) {
            return
        }

        notificationsService.notify(
            context,
            NotificationsService.NOTIFICATION_ID_SETTINGS_CHANGED,
            context.getString(R.string.settings_changed_via_api_restart_the_app_to_apply_changes)
        )
    }
}