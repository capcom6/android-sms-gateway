package me.capcom.smsgateway.ui.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.ui.dialogs.FirstStartDialogFragment
import me.capcom.smsgateway.ui.dialogs.PasswordPromptDialogFragment
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
                    showPasswordPromptDialog(
                        getString(R.string.enter_current_password),
                        PasswordPromptDialogFragment.ACTION_CHANGE_PASSWORD,
                        value
                    )
                }

                true
            }
        }

        findPreference<Preference>("gateway.clear_password")?.apply {
            isVisible = settings.hasPassword

            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                settings.clearPassword()
                isVisible = false
                listView.adapter?.notifyDataSetChanged()
                showToast(R.string.password_cleared)
                true
            }
        }

        findPreference<Preference>("gateway.login_code")?.apply {
            isVisible = settings.username != null

            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                if (settings.hasPassword) {
                    requestLoginCode()
                } else {
                    showPasswordPromptDialog(
                        getString(R.string.enter_current_password),
                        PasswordPromptDialogFragment.ACTION_LOGIN_CODE
                    )
                }
                true
            }
        }

        findPreference<Preference>("gateway.sign_in")?.apply {
            isVisible = settings.enabled

            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                FirstStartDialogFragment.newInstance()
                    .show(parentFragmentManager, "signin")
                true
            }
        }

        findPreference<Preference>("gateway.unregister")?.apply {
            isVisible = settings.registrationInfo != null

            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.unregister_device)
                    .setMessage(R.string.confirm_unregister_device)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        unregisterDevice()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
        }

        setFragmentResultListener(FirstStartDialogFragment.REQUEST_KEY) { _, data ->
            when (FirstStartDialogFragment.getResult(data)) {
                FirstStartDialogFragment.Result.Canceled -> {
                    return@setFragmentResultListener
                }

                FirstStartDialogFragment.Result.SignUp -> registerDeviceInternal(
                    GatewayService.RegistrationMode.Anonymous
                )

                FirstStartDialogFragment.Result.SignIn -> registerDeviceInternal(
                    GatewayService.RegistrationMode.WithCredentials(
                        FirstStartDialogFragment.getUsername(data),
                        FirstStartDialogFragment.getPassword(data)
                    )
                )

                FirstStartDialogFragment.Result.SignInByCode -> registerDeviceInternal(
                    GatewayService.RegistrationMode.WithCode(
                        FirstStartDialogFragment.getCode(data)
                    )
                )
            }
        }

        setFragmentResultListener(PasswordPromptDialogFragment.REQUEST_KEY) { _, data ->
            val password =
                PasswordPromptDialogFragment.getPassword(data) ?: return@setFragmentResultListener

            when (PasswordPromptDialogFragment.getAction(data)) {
                PasswordPromptDialogFragment.ACTION_CHANGE_PASSWORD -> {
                    val newPassword = PasswordPromptDialogFragment.getPayload(data)
                    if (newPassword != null) {
                        changePasswordInternal(password, newPassword)
                    }
                }

                PasswordPromptDialogFragment.ACTION_LOGIN_CODE -> {
                    requestLoginCode(password)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        updateActionsVisibility()
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

    private val onPreferenceChanged =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "gateway.ENABLED") {
                updateActionsVisibility()
            }
        }

    private fun updateActionsVisibility() {
        findPreference<Preference>("gateway.sign_in")?.isVisible = settings.enabled
        findPreference<Preference>("gateway.unregister")?.isVisible =
            settings.enabled && settings.registrationInfo != null
        listView.adapter?.notifyDataSetChanged()
    }

    private fun registerDeviceInternal(mode: GatewayService.RegistrationMode) {
        lifecycleScope.launch {
            // Capture the application context up front: the fragment may be detached by
            // the time the network call returns, so requireContext() would throw and
            // abort SSE startup and the subsequent service.start() for a registered device.
            val appContext = requireContext().applicationContext
            val previous = settings.registrationInfo
            val hadSession = previous != null
            try {
                activity?.findViewById<View>(R.id.progressBar)?.isVisible = true
                // Tear down any active SSE stream bound to the old credentials BEFORE
                // swapping them. The bearer token is now resolved fresh on every
                // SSEManager.connect() via a token provider, so the manager does NOT
                // cache stale credentials and need not be recreated for credential
                // refresh; we stop the service up front only to close the old stream.
                service.stop(appContext)
                // Explicit registration must take effect even when already registered:
                // drop stale credentials so registerDevice() does not degrade into
                // a push-token update of the current registration.
                settings.registrationInfo = null

                service.registerDevice(appContext, null, mode)

                // Revive the standard pipeline (workers + EventsReceiver) only AFTER
                // registration finished: start() is idempotent, and running it earlier
                // could race the registration event emission above.
                // Registration succeeded and is already persisted locally + remotely.
                // Post-success UI must NOT roll back credentials if the fragment detaches.
                try {
                    service.start(appContext)
                    refreshRegistrationUi()
                    showToast(R.string.device_registered_successfully)
                } catch (_: Exception) {
                    // Non-fatal: credentials and remote registration are already in place.
                }
            } catch (e: CancellationException) {
                // Lifecycle cancellation: do not blindly roll back credentials.
                // registerDevice persists locally only AFTER the network succeeds.
                // - If it completed (registrationInfo != null), the new registration
                //   is already persisted; start SSE transport if needed and rethrow.
                // - If it didn't complete (registrationInfo == null), restore the
                //   previous state and restart the old SSE stream if there was one.
                if (settings.registrationInfo != null) {
                    service.start(appContext)
                } else if (hadSession) {
                    service.stop(appContext)
                    settings.registrationInfo = previous
                    service.start(appContext)
                }
                throw e
            } catch (e: Exception) {
                service.stop(appContext)
                settings.registrationInfo = previous
                if (hadSession) {
                    service.start(appContext)
                }
                refreshRegistrationUi()
                showToast(getString(R.string.failed_to_register_device, e.message))
                return@launch
            } finally {
                // Hide the progress bar on BOTH success and failure paths. The failure
                // catch uses return@launch, so the outer finally is required to guarantee
                // the spinner is dismissed instead of lingering until the next UI refresh.
                activity?.findViewById<View>(R.id.progressBar)?.isVisible = false
            }
        }
    }

    private fun unregisterDevice() {
        settings.registrationInfo = null
        // Stop SSE session and cloud workers so they do not keep using
        // credentials of the just-unregistered device.
        service.stop(requireContext())
        refreshRegistrationUi()
        showToast(R.string.device_unregistered)
    }

    private fun refreshRegistrationUi() {
        findPreference<Preference>("gateway.clear_password")?.isVisible = settings.hasPassword
        findPreference<Preference>("gateway.login_code")?.isVisible = settings.username != null
        findPreference<Preference>("gateway.unregister")?.isVisible =
            settings.registrationInfo != null
        findPreference<Preference>("transient.device_id")?.summary =
            settings.deviceId ?: getString(R.string.n_a)
        listView.adapter?.notifyDataSetChanged()
    }

    private fun showPasswordPromptDialog(
        message: String,
        action: String? = null,
        newPassword: String? = null
    ) {
        PasswordPromptDialogFragment.newInstance(message, action, newPassword)
            .show(parentFragmentManager, "password_prompt")
    }

    private fun changePasswordInternal(currentPassword: String, newPassword: String) {
        this.lifecycleScope.launch {
            try {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = true
                service.changePassword(currentPassword, newPassword)
                findPreference<Preference>("gateway.clear_password")?.isVisible =
                    settings.hasPassword
                listView.adapter?.notifyDataSetChanged()
                showToast(R.string.password_changed_successfully)
            } catch (e: Exception) {
                showToast(getString(R.string.failed_to_change_password, e.message))
            } finally {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = false
            }
        }
    }

    private fun requestLoginCode(password: String? = null) {
        this.lifecycleScope.launch {
            try {
                requireActivity().findViewById<View>(R.id.progressBar).isVisible = true

                val loginCode = if (password == null) {
                    service.getLoginCode()
                } else {
                    service.getLoginCodeWithPassword(password)
                }
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