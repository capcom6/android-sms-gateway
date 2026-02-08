package me.stappmus.messagegateway.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import me.stappmus.messagegateway.receivers.BootReceiver

class SettingsHelper(private val context: Context) {
    private val settings = PreferenceManager.getDefaultSharedPreferences(context)

    init {
        migrate()
    }

    var autostart: Boolean
        get() = settings.getBoolean(PREF_KEY_AUTOSTART, false)
        set(value) {
            // enable broadcast receiver
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, BootReceiver::class.java),
                if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )

            settings.edit { putBoolean(PREF_KEY_AUTOSTART, value) }
        }

    private fun migrate() {
        // remove after 2025-11-28
        val PREF_KEY_SERVER_TOKEN = "server_token"
        if (settings.contains(PREF_KEY_SERVER_TOKEN)) {
            settings.edit(true) {
                putString("localserver.PASSWORD", settings.getString(PREF_KEY_SERVER_TOKEN, null))
                remove(PREF_KEY_SERVER_TOKEN)
            }
        }
    }

    companion object {
        private const val PREF_KEY_AUTOSTART = "autostart"

        private const val PREF_KEY_FCM_TOKEN = "fcm_token"
    }
}