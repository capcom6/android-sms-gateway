package me.capcom.smsgateway

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.capcom.smsgateway.databinding.ActivityMainBinding
import me.capcom.smsgateway.providers.LocalIPProvider
import me.capcom.smsgateway.providers.PublicIPProvider
import me.capcom.smsgateway.services.WebService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonStart.setOnCheckedChangeListener { _, b ->
            actionStart(b)
        }
    }

    override fun onStart() {
        super.onStart()

        LocalIPProvider(this).getIP { ip ->
            binding.textLocalIP.text = "Local IP is $ip"
        }
        PublicIPProvider().getIP { ip ->
            binding.textPublicIP.text = "Public IP is $ip"
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