package me.capcom.smsgateway.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import me.capcom.smsgateway.BuildConfig
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

        findPreference<Preference>("transient.app_version")?.summary =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            findPreference<Preference>("system")?.isEnabled = false
            findPreference<Preference>("system.disable_battery_optimizations")?.summary =
                getString(R.string.battery_optimizations_not_supported_on_this_device)
        } else {
            findPreference<Preference>("system.disable_battery_optimizations")?.summary =
                if (isIgnoringBatteryOptimizations()) getString(R.string.disabled) else getString(R.string.enabled)
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "encryption.passphrase"
            || preference.key == "gateway.private_token"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        if (preference.key == "gateway.cloud_url") {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                it.setSelectAllOnFocus(true)
            }
        }

        if (preference.key == "ping.interval_seconds"
            || preference.key == "logs.lifetime_days"
            || preference.key == "webhooks.retry_count"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "system.disable_battery_optimizations") {
            requestIgnoreBatteryOptimizations()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(
                requireContext(),
                getString(R.string.battery_optimizations_not_supported_on_this_device),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.battery_optimizations_already_disabled),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${requireContext().packageName}")
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}