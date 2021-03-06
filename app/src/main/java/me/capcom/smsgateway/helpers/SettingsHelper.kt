package me.capcom.smsgateway.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import me.capcom.smsgateway.receivers.BootReceiver
import java.util.*

class SettingsHelper(private val context: Context) {
    private val settings = PreferenceManager.getDefaultSharedPreferences(context)

    var autostart: Boolean
        get() = settings.getBoolean(PREF_KEY_AUTOSTART, false)
        set(value) {
            // enable broadcast receiver
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, BootReceiver::class.java),
                if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )

            settings.edit().putBoolean(PREF_KEY_AUTOSTART, value).apply()
        }

    var serverPort: Int
        get() = settings.getInt(PREF_KEY_SERVER_PORT, 8080)
        set(value) = settings.edit().putInt(PREF_KEY_SERVER_PORT, value).apply()

    var serverToken: String
        get() = settings.getString(PREF_KEY_SERVER_TOKEN, null) ?: UUID.randomUUID().toString().substring(0, 8).also { serverToken = it }
        set(value) = settings.edit().putString(PREF_KEY_SERVER_TOKEN, value).apply()


    companion object {
        private const val PREF_KEY_AUTOSTART = "autostart"

        private const val PREF_KEY_SERVER_PORT = "server_port"
        private const val PREF_KEY_SERVER_TOKEN = "server_token"
    }
}