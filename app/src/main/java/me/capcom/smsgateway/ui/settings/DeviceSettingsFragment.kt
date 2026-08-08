package me.capcom.smsgateway.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.device.DeviceService
import org.koin.android.ext.android.inject

class DeviceSettingsFragment : BasePreferenceFragment() {
    private val deviceService: DeviceService by inject()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.device_preferences, null)
    }

    override fun onResume() {
        super.onResume()

        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(
            onPreferenceChanged
        )

        refreshFingerprintSummary()
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(
            onPreferenceChanged
        )

        super.onPause()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        super.onDisplayPreferenceDialog(preference)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "device.rotate_key") {
            rotateEncryptionKey()
            return true
        }

        if (preference.key == "device.key_fingerprint") {
            copyFingerprint()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun rotateEncryptionKey() {
        lifecycleScope.launch {
            try {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = true

                deviceService.rotateKey()
                refreshFingerprintSummary()

                showToast(R.string.key_rotated_successfully)
            } catch (e: Exception) {
                showToast(getString(R.string.key_rotation_failed, e.message))
            } finally {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = false
            }
        }
    }

    /**
     * Refreshes the fingerprint preference summary from the current active
     * device key, or shows the "not available" string when no fingerprint
     * can be computed.
     */
    private fun refreshFingerprintSummary() {
        lifecycleScope.launch {
            val fingerprint = deviceService.getPublicKeyFingerprint()
            findPreference<Preference>("device.key_fingerprint")?.summary =
                fingerprint ?: getString(R.string.key_fingerprint_not_available)
        }
    }

    /**
     * Copies the current device key fingerprint to the clipboard and shows
     * a toast. Does nothing when no fingerprint is available.
     */
    private fun copyFingerprint() {
        lifecycleScope.launch {
            val fingerprint = deviceService.getPublicKeyFingerprint() ?: return@launch
            val clipboard = requireContext().getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Key fingerprint", fingerprint))
            showToast(R.string.key_fingerprint_copied)
        }
    }

    private val onPreferenceChanged =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "device.rotate_interval_days") {
                deviceService.start(requireContext())
            }
        }
}
