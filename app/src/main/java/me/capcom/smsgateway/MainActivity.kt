package me.capcom.smsgateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.capcom.smsgateway.databinding.ActivityMainBinding
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.localserver.events.IPReceivedEvent

class MainActivity : AppCompatActivity() {

    private val settingsHelper by lazy { SettingsHelper(this) }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

//        WebService.STATUS.observe(this) {
//            binding.buttonStart.isChecked = it
//        }

        if (settingsHelper.autostart) {
            actionStart(true)
        }

        lifecycleScope.launch {
            App.instance.gatewayModule.events.events.collect { event ->
                val event = event as? DeviceRegisteredEvent ?: return@collect

                binding.textRemoteAuth.text = "Basic auth ${event.login}:${event.password}"
            }
        }

        lifecycleScope.launch {
            App.instance.localServerModule.events.events.collect { event ->
                val event = event as? IPReceivedEvent ?: return@collect

                binding.textLocalIP.text = "Local address is ${event.localIP}:${settingsHelper.serverPort}"
                binding.textPublicIP.text = "Public address is ${event.publicIP}:${settingsHelper.serverPort}"
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            }

            App.instance.gatewayModule.start(this)
            App.instance.localServerModule.start(this)
        } else {
            App.instance.localServerModule.stop(this)
            App.instance.gatewayModule.stop(this)
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

}