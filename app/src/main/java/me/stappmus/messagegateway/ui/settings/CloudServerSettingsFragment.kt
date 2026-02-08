package me.stappmus.messagegateway.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import kotlinx.coroutines.launch
import me.stappmus.messagegateway.R
import me.stappmus.messagegateway.modules.gateway.GatewayService
import me.stappmus.messagegateway.modules.gateway.GatewaySettings
import org.koin.android.ext.android.inject
import java.net.URL
import java.text.DateFormat

class CloudServerSettingsFragment : BasePreferenceFragment() {

    private val settings: GatewaySettings by inject()
    private val service: GatewayService by inject()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.cloud_server_preferences, rootKey)

        findPreference<Preference>("transient.device_id")?.summary =
            settings.deviceId ?: getString(R.string.n_a)

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


        findPreference<EditTextPreference>("gateway.cloud_url")?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as? String
            if (value.isNullOrEmpty()) {
                return@setOnPreferenceChangeListener true
            }

            try {
                URL(value)
            } catch (e: Exception) {
                showToast(getString(R.string.invalid_url))
                return@setOnPreferenceChangeListener false
            }

            true
        }

        findPreference<EditTextPreference>("gateway.username")?.setSummaryProvider {
            settings.username ?: getString(R.string.not_set)
        }
        findPreference<EditTextPreference>("gateway.password")?.apply {
            isEnabled = settings.username != null && settings.password != null

            setSummaryProvider {
                when {
                    settings.username == null -> getString(R.string.not_registered)
                    settings.username != null && settings.password == null -> "Can't be changed"
                    else -> settings.password
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as? String
                if (value == null || value.length < 14) {
                    showToast(getString(R.string.password_must_be_at_least_14_characters))
                    return@setOnPreferenceChangeListener false
                }

                this@CloudServerSettingsFragment.lifecycleScope.launch {
                    try {
                        requireActivity().findViewById<View>(R.id.progressBar).isVisible = true
                        service.changePassword(settings.password ?: "", value)
                        listView.adapter?.notifyDataSetChanged()
                        showToast(getString(R.string.password_changed_successfully))
                    } catch (e: Exception) {
                        showToast(getString(R.string.failed_to_change_password, e.message))
                    } finally {
                        requireActivity().findViewById<View>(R.id.progressBar).isVisible = false
                    }
                }

                true
            }
        }

        findPreference<Preference>("gateway.login_code")?.apply {
            isVisible = settings.username != null && settings.password != null

            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                this@CloudServerSettingsFragment.lifecycleScope.launch {
                    try {
                        requireActivity().findViewById<View>(R.id.progressBar).isVisible = true

                        val loginCode = service.getLoginCode()
                        title = getString(
                            R.string.login_code_expires,
                            DateFormat.getDateTimeInstance().format(loginCode.validUntil)
                        )
                        summary = loginCode.code

                        listView.adapter?.notifyDataSetChanged()
                        showToast(getString(R.string.success_long_press_to_copy))
                    } catch (e: Exception) {
                        showToast(getString(R.string.failed_to_get_login_code, e.message))
                    } finally {
                        requireActivity().findViewById<View>(R.id.progressBar).isVisible = false
                    }
                }

                true
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "gateway.cloud_url") {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        if (preference.key == "gateway.private_token") {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        if (preference.key == "gateway.password") {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT
                it.text = null
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }
}