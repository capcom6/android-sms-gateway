package me.capcom.smsgateway.modules.settings

import androidx.preference.PreferenceManager
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.messages.MessagesSettings
import me.capcom.smsgateway.modules.ping.PingSettings
import org.koin.dsl.module

val settingsModule = module {
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
}