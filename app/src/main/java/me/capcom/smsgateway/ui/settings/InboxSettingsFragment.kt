package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.receiver.ReceiverSettings

class InboxSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.inbox_preferences, rootKey)

        findPreference<EditTextPreference>(KEY_MMS_MAX_INLINE_ATTACHMENT_BYTES)
            ?.setOnPreferenceChangeListener { _, newValue ->
                val bytes = newValue?.toString()?.trim()?.toLongOrNull()
                if (bytes == null || bytes !in 0..ReceiverSettings.MAX_INLINE_ATTACHMENT_BYTES) {
                    showToast(getString(R.string.mms_max_inline_attachment_size_invalid))
                    false
                } else {
                    true
                }
            }
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

            KEY_MMS_MAX_INLINE_ATTACHMENT_BYTES -> {
                (preference as EditTextPreference).setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                    it.setSelectAllOnFocus(true)
                    it.selectAll()
                }
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }

    companion object {
        private const val KEY_MMS_MAX_INLINE_ATTACHMENT_BYTES = "receiver.mms_max_inline_attachment_bytes"
    }
}
