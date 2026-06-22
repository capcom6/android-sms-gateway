package me.capcom.smsgateway.ui.settings

import android.app.TimePickerDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import me.capcom.smsgateway.R

class MessagesSettingsFragment : BasePreferenceFragment() {

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
        setPreferencesFromResource(R.xml.messages_preferences, rootKey)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference.key) {
            "messages.limit_value",
            "messages.log_lifetime_days",
            "messages.send_interval_min",
            "messages.send_interval_max" -> {
                (preference as EditTextPreference).setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                    it.setSelectAllOnFocus(true)
                    it.selectAll()
                }
            }

            "messages.work_hours_start", "messages.work_hours_end" -> {
                val pref = preference as EditTextPreference
                val currentText = pref.text ?: "09:00"
                val parts = currentText.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
                val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

                TimePickerDialog(
                    requireContext(),
                    { _, h, m ->
                        pref.text = String.format(java.util.Locale.US, "%02d:%02d", h, m)
                    },
                    hour,
                    minute,
                    true
                ).show()
                return
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
}