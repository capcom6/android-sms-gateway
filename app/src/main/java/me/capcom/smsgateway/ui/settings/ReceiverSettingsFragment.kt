package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import me.capcom.smsgateway.R

class ReceiverSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.receiver_preferences, rootKey)
    }
}
