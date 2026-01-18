package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import me.capcom.smsgateway.R

class NotificationSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notification_preferences, rootKey)
    }
}