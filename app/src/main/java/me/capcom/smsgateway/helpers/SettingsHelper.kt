package me.capcom.smsgateway.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import me.capcom.smsgateway.receivers.BootReceiver
import java.util.Locale

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

    var language: String
        get() {
            return when (val stored = settings.getString(APP_LANGUAGE, "")) {
                null, "system" -> ""   // keep legacy "system" compatible
                else -> stored         // "" means "follow system"
            }
        }
        set(value) = settings.edit(true) {
            putString(APP_LANGUAGE, value)
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

        private const val APP_LANGUAGE = "app.language"
    }
}