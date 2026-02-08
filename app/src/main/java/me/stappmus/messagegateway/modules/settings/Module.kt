package me.stappmus.messagegateway.modules.settings

import androidx.preference.PreferenceManager
import me.stappmus.messagegateway.helpers.SettingsHelper
import me.stappmus.messagegateway.modules.encryption.EncryptionSettings
import me.stappmus.messagegateway.modules.gateway.GatewaySettings
import me.stappmus.messagegateway.modules.localserver.LocalServerSettings
import me.stappmus.messagegateway.modules.logs.LogsSettings
import me.stappmus.messagegateway.modules.media.MediaSettings
import me.stappmus.messagegateway.modules.messages.MessagesSettings
import me.stappmus.messagegateway.modules.ping.PingSettings
import me.stappmus.messagegateway.modules.webhooks.TemporaryStorage
import me.stappmus.messagegateway.modules.webhooks.WebhooksSettings
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


val settingsModule = module {
    singleOf(::SettingsService)
    factory { PreferenceManager.getDefaultSharedPreferences(get()) }
    factory { SettingsHelper(get()) }

    factory {
        EncryptionSettings(
            PreferencesStorage(get(), "encryption")
        )
    }
    factory {
        GatewaySettings(
            PreferencesStorage(get(), "gateway")
        )
    }
    factory {
        MessagesSettings(
            PreferencesStorage(get(), "messages")
        )
    }
    factory {
        LocalServerSettings(
            PreferencesStorage(get(), "localserver")
        )
    }
    factory {
        PingSettings(
            PreferencesStorage(get(), "ping")
        )
    }
    factory {
        LogsSettings(
            PreferencesStorage(get(), "logs")
        )
    }
    factory {
        MediaSettings(
            PreferencesStorage(get(), "media")
        )
    }
    factory {
        WebhooksSettings(
            PreferencesStorage(get(), "webhooks")
        )
    }
    factory {
        TemporaryStorage(
            PreferencesStorage(get(), "webhooks")
        )
    }
}
