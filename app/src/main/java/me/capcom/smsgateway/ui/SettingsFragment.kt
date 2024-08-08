package me.capcom.smsgateway.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.gateway.GatewaySettings

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onResume() {
        super.onResume()

        onPreferenceChanged.onSharedPreferenceChanged(
            preferenceManager.sharedPreferences,
            "messages.limit_period"
        )
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(
            onPreferenceChanged
        )
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(
            onPreferenceChanged
        )

        super.onPause()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<EditTextPreference>("gateway.cloud_url")?.setSummaryProvider {
            val hostname = preferenceManager.sharedPreferences?.getString(it.key, null)
            if (hostname.isNullOrEmpty()) {
                preferenceManager.sharedPreferences?.edit(true) {
                    putString(it.key, GatewaySettings.PUBLIC_URL)
                }
                return@setSummaryProvider GatewaySettings.PUBLIC_URL
            }
            return@setSummaryProvider hostname
        }

        findPreference<Preference>("transient.app_version")?.summary =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "encryption.passphrase"
            || preference.key == "gateway.private_token"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        if (preference.key == "gateway.cloud_url") {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                it.setSelectAllOnFocus(true)
            }
        }

        if (preference.key == "messages.limit_value"
            || preference.key == "messages.log_lifetime_days"
            || preference.key == "messages.send_interval_min"
            || preference.key == "messages.send_interval_max"
            || preference.key == "ping.interval_seconds"
            || preference.key == "logs.lifetime_days"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }

    private val onPreferenceChanged =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "messages.limit_period") {
                findPreference<EditTextPreference>("messages.limit_value")?.isEnabled =
                    sharedPreferences?.getString(key, "Disabled") != "Disabled"
            }
        }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}