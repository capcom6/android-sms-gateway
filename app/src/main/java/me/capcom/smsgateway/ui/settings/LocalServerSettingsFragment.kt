package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.localserver.auth.AuthScopes
import org.koin.android.ext.android.inject

class LocalServerSettingsFragment : BasePreferenceFragment() {
    private val settings by inject<LocalServerSettings>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.local_server_preferences, rootKey)

        findPreference<Preference>("transient.device_id")?.summary =
            settings.deviceId ?: getString(R.string.n_a)
        findPreference<Preference>("transient.jwt_secret")?.summary =
            settings.jwtSecret
        findPreference<Preference>("transient.jwt_regenerate_secret")?.setOnPreferenceClickListener {
            val newSecret = settings.regenerateJwtSecret()
            findPreference<Preference>("transient.jwt_secret")?.summary = newSecret
            showToast(getString(R.string.jwt_secret_regenerated))
            true
        }
        findPreference<Preference>("transient.jwt_reset_default_scopes")?.setOnPreferenceClickListener {
            val restored = AuthScopes.allowed
                .filter { it != AuthScopes.ALL_ANY }
                .joinToString(",")
            settings.jwtDefaultScopes = restored
            findPreference<EditTextPreference>("localserver.JWT_DEFAULT_SCOPES")?.text = restored
            showToast(getString(R.string.jwt_default_scopes_restored))
            true
        }

        findPreference<EditTextPreference>("localserver.PORT")?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as? String
            val intValue = value?.toIntOrNull()
            if (intValue == null || intValue < 1024 || intValue > 65535) {
                showToast(
                    getString(
                        R.string.is_not_a_valid_port_must_be_between_1024_and_65535,
                        value
                    )
                )
                return@setOnPreferenceChangeListener false
            }

            true
        }

        findPreference<EditTextPreference>("localserver.USERNAME")?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as? String
            if ((value?.length ?: 0) < 3) {
                showToast(getString(R.string.username_must_be_at_least_3_characters))
                return@setOnPreferenceChangeListener false
            }

            true
        }
        findPreference<EditTextPreference>("localserver.PASSWORD")?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as? String
            if ((value?.length ?: 0) < 8) {
                showToast(getString(R.string.password_must_be_at_least_8_characters))
                return@setOnPreferenceChangeListener false
            }

            true
        }

        findPreference<EditTextPreference>("localserver.JWT_ISSUER")?.setOnPreferenceChangeListener { _, newValue ->
            val value = (newValue as? String)?.trim().orEmpty()
            if (value.isEmpty()) {
                showToast(getString(R.string.jwt_issuer_must_not_be_empty))
                return@setOnPreferenceChangeListener false
            }

            true
        }
        findPreference<EditTextPreference>("localserver.JWT_TTL_SECONDS")?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as? String
            val ttl = value?.toLongOrNull()
            if (ttl == null || ttl <= 0) {
                showToast(getString(R.string.jwt_ttl_must_be_positive_seconds))
                return@setOnPreferenceChangeListener false
            }

            true
        }
        findPreference<EditTextPreference>("localserver.JWT_DEFAULT_SCOPES")?.setOnPreferenceChangeListener { _, newValue ->
            val value = (newValue as? String).orEmpty()
            val scopes = AuthScopes.parseCsv(value)

            if (scopes.isEmpty()) {
                showToast(getString(R.string.jwt_scopes_must_not_be_empty))
                return@setOnPreferenceChangeListener false
            }

            val invalidScope = AuthScopes.firstUnsupported(scopes)
            if (invalidScope != null) {
                showToast(getString(R.string.jwt_scope_is_not_supported, invalidScope))
                return@setOnPreferenceChangeListener false
            }

            true
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "localserver.PORT"
            || preference.key == "localserver.JWT_TTL_SECONDS"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        if (preference.key == "localserver.PASSWORD"
            || preference.key == "localserver.USERNAME"
            || preference.key == "localserver.JWT_ISSUER"
            || preference.key == "localserver.JWT_DEFAULT_SCOPES"
        ) {
            (preference as EditTextPreference).setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT
                it.setSelectAllOnFocus(true)
                it.selectAll()
            }
        }

        super.onDisplayPreferenceDialog(preference)
    }
}