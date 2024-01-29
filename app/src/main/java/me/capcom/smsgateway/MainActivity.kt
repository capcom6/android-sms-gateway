package me.capcom.smsgateway

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import me.capcom.smsgateway.databinding.ActivityMainBinding
import me.capcom.smsgateway.ui.AdvancedSettingsFragment
import me.capcom.smsgateway.ui.HolderFragment
import me.capcom.smsgateway.ui.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = FragmentsAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.apply {
                    text = getString(R.string.tab_text_settings)
                    setIcon(R.drawable.ic_settings_24)
                }

                1 -> tab.apply {
                    text = getString(R.string.tab_text_messages)
                    setIcon(R.drawable.ic_sms)
                }

                2 -> tab.apply {
                    text = getString(R.string.tab_text_advanced)
                    setIcon(R.drawable.ic_advanced)
                }
            }
        }.attach()
    }

    class FragmentsAdapter(activity: AppCompatActivity) :
        androidx.viewpager2.adapter.FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SettingsFragment.newInstance()
                1 -> HolderFragment.newInstance()
                else -> AdvancedSettingsFragment.newInstance()
            }
        }

    }
}