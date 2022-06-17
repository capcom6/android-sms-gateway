package me.capcom.smsgateway

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
    }

    override fun onStart() {
        super.onStart()

        LocalIPProvider(this).getIP { ip ->
            binding.textLocalIP.text = "Local address is $ip:${settingsHelper.serverPort}"
        }
        PublicIPProvider().getIP { ip ->
            binding.textPublicIP.text = "Public address is $ip:${settingsHelper.serverPort}"
        }
    }

    private fun actionStart(start: Boolean) {
        if (start) {
            WebService.start(this)
        } else {
            WebService.stop(this)
        }
    }
}