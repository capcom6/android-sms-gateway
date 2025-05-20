package me.capcom.smsgateway.modules.settings

import android.content.Context
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
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
    messagesSettings: MessagesSettings,
    localServerSettings: LocalServerSettings,
    pingSettings: PingSettings,
    logsSettings: LogsSettings,
    webhooksSettings: WebhooksSettings
) {
    private val settings = mapOf(
        "encryption" to encryptionSettings,
        "gateway" to gatewaySettings,
        "messages" to messagesSettings,
        "localserver" to localServerSettings,
        "ping" to pingSettings,
        "logs" to logsSettings,
        "webhooks" to webhooksSettings
    )

    fun getAll(): Map<String, *> {
        return settings.mapValues { (it.value as? Exporter)?.export() }
    }

    fun apply(data: Map<String, *>) {
        data.forEach { (key, value) ->
            settings[key]?.let {
                (it as? Importer)?.import(value as Map<String, *>)
            }
        }

        notificationsService.notify(
            context,
            NotificationsService.NOTIFICATION_ID_SETTINGS_CHANGED,
            "Settings changed via API. Restart the app to apply changes."
        )
    }
}