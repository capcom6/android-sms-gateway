package me.capcom.smsgateway.ui

import android.os.Bundle
import android.text.InputType
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.gateway.GatewaySettings

class SettingsFragment : PreferenceFragmentCompat() {

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
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "encryption.passphrase"
            || preference.key == "gateway.private_token"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

        if (preference.key == "gateway.cloud_url") {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}