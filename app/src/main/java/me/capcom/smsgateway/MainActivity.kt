package me.capcom.smsgateway

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.capcom.smsgateway.databinding.ActivityMainBinding
import me.capcom.smsgateway.services.WebService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonStart.setOnCheckedChangeListener { _, b ->
            actionStart(b)
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