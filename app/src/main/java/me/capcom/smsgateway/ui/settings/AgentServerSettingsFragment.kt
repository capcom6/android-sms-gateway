package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.data.dao.ServerSettingsDao
import me.capcom.smsgateway.data.entities.ServerSettings
import me.capcom.smsgateway.services.AgentService // For Intent actions
import org.koin.android.ext.android.inject

class AgentServerSettingsFragment : PreferenceFragmentCompat() {

    private val serverSettingsDao: ServerSettingsDao by inject()
    // private val ktorServerClient: KtorServerClient by inject() // For direct registration if needed in future

    private lateinit var serverUrlPreference: EditTextPreference
    private lateinit var agentIdPreference: Preference
    private lateinit var apiKeyPreference: Preference
    private lateinit var saveRegisterPreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.agent_server_preferences, rootKey)

        serverUrlPreference = findPreference("pref_key_server_url")!!
        agentIdPreference = findPreference("pref_key_agent_id_display")!!
        apiKeyPreference = findPreference("pref_key_api_key_display")!!
        saveRegisterPreference = findPreference("pref_key_save_register")!!

        // Configure serverUrlPreference to accept any URI
        serverUrlPreference.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        loadAndObserveSettings()

        saveRegisterPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            handleSaveAndRegister()
            true
        }
    }

    private fun loadAndObserveSettings() {
        lifecycleScope.launchWhenStarted {
            serverSettingsDao.getSettings().collectLatest { settings ->
                if (settings != null) {
                    serverUrlPreference.text = settings.serverUrl
                    if (!settings.agentId.isNullOrBlank()) {
                        agentIdPreference.summary = settings.agentId
                    } else {
                        agentIdPreference.summary = getString(R.string.pref_summary_not_registered)
                    }
                    if (!settings.apiKey.isNullOrBlank()) {
                        // Mask API Key: Show only a few characters or asterisks
                        apiKeyPreference.summary = "****${settings.apiKey.takeLast(4)}"
                    } else {
                        apiKeyPreference.summary = getString(R.string.pref_summary_not_registered)
                    }
                } else {
                    serverUrlPreference.text = null
                    agentIdPreference.summary = getString(R.string.pref_summary_not_registered)
                    apiKeyPreference.summary = getString(R.string.pref_summary_not_registered)
                }
            }
        }
    }

    private fun handleSaveAndRegister() {
        val newServerUrl = serverUrlPreference.text?.trim()

        if (newServerUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.toast_server_url_empty), Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            val currentSettings = serverSettingsDao.getSettingsDirect()
            var settingsModified = false
            var forceReRegistration = false

            if (currentSettings == null || currentSettings.serverUrl != newServerUrl) {
                // New URL or URL changed, clear old agentId and apiKey to force re-registration
                val settingsToSave = ServerSettings(
                    serverUrl = newServerUrl,
                    agentId = null, // Clear to force re-registration
                    apiKey = null   // Clear to force re-registration
                )
                serverSettingsDao.insert(settingsToSave)
                settingsModified = true
                forceReRegistration = true
                Toast.makeText(requireContext(), getString(R.string.toast_settings_saved_will_register), Toast.LENGTH_LONG).show()
            } else {
                // URL is the same, just a "save" click, perhaps to trigger registration if it failed previously
                if (currentSettings.agentId.isNullOrBlank() || currentSettings.apiKey.isNullOrBlank()){
                    forceReRegistration = true // Agent not yet registered, try again
                    Toast.makeText(requireContext(), getString(R.string.toast_registration_attempt_triggered), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.toast_settings_unchanged), Toast.LENGTH_SHORT).show()
                }
            }

            if (forceReRegistration) {
                // Trigger AgentService to re-initialize/re-register.
                // AgentService should observe ServerSettingsDao or be explicitly triggered.
                // Forcing a restart of the agent logic is one way if direct communication is complex.
                // A simpler way is that AgentService's periodic config check will pick up the cleared agentId/apiKey.
                // Or, send an intent to AgentService.
                val serviceIntent = Intent(requireContext(), AgentService::class.java).apply {
                    action = AgentService.ACTION_START // This will trigger re-initialization if agentId/apiKey are null
                }
                requireContext().startService(serviceIntent)
                 me.capcom.smsgateway.modules.localsms.utils.Logger.get(this.javaClass.simpleName).info("Sent ACTION_START to AgentService to trigger re-registration if needed.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh summaries on resume, in case AgentService updated them
        lifecycleScope.launch {
            val settings = serverSettingsDao.getSettingsDirect()
            if (settings != null) {
                serverUrlPreference.text = settings.serverUrl
                 if (!settings.agentId.isNullOrBlank()) {
                    agentIdPreference.summary = settings.agentId
                } else {
                    agentIdPreference.summary = getString(R.string.pref_summary_not_registered)
                }
                if (!settings.apiKey.isNullOrBlank()) {
                    apiKeyPreference.summary = "****${settings.apiKey.takeLast(4)}"
                } else {
                    apiKeyPreference.summary = getString(R.string.pref_summary_not_registered)
                }
            }
        }
    }
}
