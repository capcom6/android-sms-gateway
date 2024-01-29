package me.capcom.smsgateway.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import me.capcom.smsgateway.R

class AdvancedSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    companion object {
        fun newInstance() = AdvancedSettingsFragment()
    }
}