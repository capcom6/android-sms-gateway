package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.encryption.E2EKeyService
import org.koin.android.ext.android.inject

class EncryptionSettingsFragment : BasePreferenceFragment() {
    private val e2eKeySvc: E2EKeyService by inject()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.encryption_preferences, null)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "encryption.passphrase") {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "encryption.rotate_key") {
            rotateEncryptionKey()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun rotateEncryptionKey() {
        lifecycleScope.launch {
            try {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = true

                e2eKeySvc.rotateKey()

                showToast(R.string.key_rotated_successfully)
            } catch (e: Exception) {
                showToast(getString(R.string.key_rotation_failed, e.message))
            } finally {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = false
            }
        }
    }
}
