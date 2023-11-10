package me.capcom.smsgateway.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.capcom.smsgateway.App
import me.capcom.smsgateway.databinding.FragmentSettingsBinding
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.localserver.events.IPReceivedEvent

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val settingsHelper by lazy { SettingsHelper(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textAuthentication.text = "Basic auth sms:${settingsHelper.serverToken}"
        binding.switchAutostart.isChecked = settingsHelper.autostart

        binding.switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            settingsHelper.autostart = isChecked
        }
        binding.switchUseRemoteServer.setOnCheckedChangeListener { _, isChecked ->
            App.instance.gatewayModule.enabled = isChecked
            binding.layoutRemoteServer.isVisible = isChecked
        }
        binding.switchUseLocalServer.setOnCheckedChangeListener { _, isChecked ->
            App.instance.localServerModule.enabled = isChecked
            binding.layoutLocalServer.isVisible = isChecked
        }

        binding.buttonStart.setOnCheckedChangeListener { _, b ->
            actionStart(b)
        }

        if (settingsHelper.autostart) {
            actionStart(true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            App.instance.gatewayModule.events.events.collect { event ->
                val event = event as? DeviceRegisteredEvent ?: return@collect

                binding.textRemoteAuth.text = "Basic auth ${event.login}:${event.password}"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            App.instance.localServerModule.events.events.collect { event ->
                val event = event as? IPReceivedEvent ?: return@collect

                binding.textLocalIP.text =
                    "Local address is ${event.localIP}:${settingsHelper.serverPort}"
                binding.textPublicIP.text =
                    "Public address is ${event.publicIP}:${settingsHelper.serverPort}"
            }
        }
    }

    override fun onResume() {
        super.onResume()

        binding.switchUseRemoteServer.isChecked = App.instance.gatewayModule.enabled
        binding.switchUseLocalServer.isChecked = App.instance.localServerModule.enabled
    }

    private fun actionStart(start: Boolean) {
        if (start) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            }

            App.instance.gatewayModule.start(requireContext())
            App.instance.localServerModule.start(requireContext())
        } else {
            App.instance.localServerModule.stop(requireContext())
            App.instance.gatewayModule.stop(requireContext())
        }
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher. You can use either a val, as shown in this snippet,
    // or a lateinit var in your onAttach() or onCreate() method.
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
                Log.d(javaClass.name, "Permissions granted")
            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
                Log.d(javaClass.name, "Permissions is not granted")
            }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            SettingsFragment()
    }
}