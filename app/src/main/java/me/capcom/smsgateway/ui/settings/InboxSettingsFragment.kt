package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import me.capcom.smsgateway.R

class InboxSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.inbox_preferences, rootKey)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference.key) {
            "incoming.lifetime_days" -> {
                (preference as EditTextPreference).setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                    it.setSelectAllOnFocus(true)
                    it.selectAll()
                }
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }
}
