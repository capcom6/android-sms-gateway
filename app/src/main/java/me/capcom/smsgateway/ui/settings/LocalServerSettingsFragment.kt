package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import me.capcom.smsgateway.R

class LocalServerSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.local_server_preferences, rootKey)

        findPreference<EditTextPreference>("localserver.PORT")?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as? String
            val intValue = value?.toIntOrNull()
            if (intValue == null || intValue < 1024 || intValue > 65535) {
                showToast(
                    getString(
                        R.string.is_not_a_valid_port_must_be_between_1024_and_65535,
                        value
                    )
                )
                return@setOnPreferenceChangeListener false
            }

            true
        }

        findPreference<EditTextPreference>("localserver.USERNAME")?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as? String
            if ((value?.length ?: 0) < 3) {
                showToast(getString(R.string.username_must_be_at_least_3_characters))
                return@setOnPreferenceChangeListener false
            }

            true
        }
        findPreference<EditTextPreference>("localserver.PASSWORD")?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as? String
            if ((value?.length ?: 0) < 8) {
                showToast(getString(R.string.password_must_be_at_least_8_characters))
                return@setOnPreferenceChangeListener false
            }

            true
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "localserver.PORT"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        if (preference.key == "localserver.PASSWORD"
            || preference.key == "localserver.USERNAME"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }
}