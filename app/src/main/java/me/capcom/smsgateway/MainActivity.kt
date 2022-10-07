package me.capcom.smsgateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.capcom.smsgateway.databinding.ActivityMainBinding
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.providers.LocalIPProvider
import me.capcom.smsgateway.providers.PublicIPProvider
import me.capcom.smsgateway.services.WebService

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

        binding.buttonStart.setOnCheckedChangeListener { _, b ->
            actionStart(b)
        }

        WebService.STATUS.observe(this) {
            binding.buttonStart.isChecked = it
        }

        if (settingsHelper.autostart) {
            actionStart(true)
        }
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch(Dispatchers.IO) {
            LocalIPProvider(this@MainActivity).getIP()?.let { ip ->
                launch(Dispatchers.Main) {
                    binding.textLocalIP.text = "Local address is $ip:${settingsHelper.serverPort}"
                }
            }
            PublicIPProvider().getIP()?.let { ip ->
                launch(Dispatchers.Main) {
                    binding.textPublicIP.text = "Public address is $ip:${settingsHelper.serverPort}"
                }
            }
        }
    }

    private fun actionStart(start: Boolean) {
        if (start) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            }

            WebService.start(this)
        } else {
            WebService.stop(this)
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