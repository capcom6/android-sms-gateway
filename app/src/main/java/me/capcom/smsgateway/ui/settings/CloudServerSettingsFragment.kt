package me.capcom.smsgateway.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.ui.dialogs.PasswordPromptDialogFragment
import org.koin.android.ext.android.inject
import java.net.URL
import java.text.DateFormat

class CloudServerSettingsFragment : BasePreferenceFragment() {

    private val settings: GatewaySettings by inject()
    private val service: GatewayService by inject()

    private var pendingPasswordChange: String? = null
    private var pendingLoginCodeRequest = false

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
            isEnabled = settings.username != null

            setSummaryProvider {
                when {
                    settings.username == null -> getString(R.string.not_registered)
                    !settings.hasPassword -> getString(R.string.password_hidden)
                    else -> settings.password
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as? String
                if (value == null || value.length < 14) {
                    showToast(getString(R.string.password_must_be_at_least_14_characters))
                    return@setOnPreferenceChangeListener false
                }

                if (settings.hasPassword) {
                    changePasswordInternal(settings.password!!, value)
                } else {
                    pendingPasswordChange = value
                    showPasswordPromptDialog(getString(R.string.enter_current_password))
                }

                true
            }
        }

        findPreference<Preference>("gateway.clear_password")?.apply {
            isVisible = settings.hasPassword

            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                settings.clearPassword()
                listView.adapter?.notifyDataSetChanged()
                showToast(R.string.password_cleared)
                true
            }
        }

        findPreference<Preference>("gateway.login_code")?.apply {
            isVisible = settings.username != null

            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                if (settings.hasPassword) {
                    requestLoginCodeInternal()
                } else {
                    pendingLoginCodeRequest = true
                    showPasswordPromptDialog(getString(R.string.enter_current_password))
                }
                true
            }
        }

        setFragmentResultListener(PasswordPromptDialogFragment.REQUEST_KEY) { _, data ->
            val password = PasswordPromptDialogFragment.getPassword(data)
            if (password == null) {
                pendingPasswordChange = null
                pendingLoginCodeRequest = false
                return@setFragmentResultListener
            }

            if (pendingPasswordChange != null) {
                changePasswordInternal(password, pendingPasswordChange!!)
                pendingPasswordChange = null
            } else if (pendingLoginCodeRequest) {
                requestLoginCodeWithPassword(password)
                pendingLoginCodeRequest = false
            }
        }
    }

    private fun showPasswordPromptDialog(message: String) {
        PasswordPromptDialogFragment.newInstance(message)
            .show(parentFragmentManager, "password_prompt")
    }

    private fun changePasswordInternal(currentPassword: String, newPassword: String) {
        this.lifecycleScope.launch {
            try {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = true
                service.changePassword(currentPassword, newPassword)
                listView.adapter?.notifyDataSetChanged()
                showToast(R.string.password_changed_successfully)
            } catch (e: Exception) {
                showToast(getString(R.string.failed_to_change_password, e.message))
            } finally {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = false
            }
        }
    }

    private fun requestLoginCodeInternal() {
        this.lifecycleScope.launch {
            try {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = true

                val loginCode = service.getLoginCode()
                findPreference<Preference>("gateway.login_code")?.title = getString(
                    R.string.login_code_expires,
                    DateFormat.getDateTimeInstance().format(loginCode.validUntil)
                )
                findPreference<Preference>("gateway.login_code")?.summary = loginCode.code

                listView.adapter?.notifyDataSetChanged()
                showToast(R.string.success_long_press_to_copy)
            } catch (e: Exception) {
                showToast(getString(R.string.failed_to_get_login_code, e.message))
            } finally {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = false
            }
        }
    }

    private fun requestLoginCodeWithPassword(password: String) {
        this.lifecycleScope.launch {
            try {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = true

                val loginCode = service.getLoginCodeWithPassword(password)
                findPreference<Preference>("gateway.login_code")?.title = getString(
                    R.string.login_code_expires,
                    DateFormat.getDateTimeInstance().format(loginCode.validUntil)
                )
                findPreference<Preference>("gateway.login_code")?.summary = loginCode.code

                listView.adapter?.notifyDataSetChanged()
                showToast(R.string.success_long_press_to_copy)
            } catch (e: Exception) {
                showToast(getString(R.string.failed_to_get_login_code, e.message))
            } finally {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = false
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