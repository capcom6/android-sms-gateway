package me.capcom.smsgateway

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import me.capcom.smsgateway.databinding.ActivityMainBinding
import me.capcom.smsgateway.ui.MessagesListFragment
import me.capcom.smsgateway.ui.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = FragmentsAdapter(this)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.apply {
                    text = getString(R.string.tab_text_settings)
                    setIcon(R.drawable.ic_settings_24)
                }

                else -> tab.apply {
                    text = getString(R.string.tab_text_messages)
                    setIcon(R.drawable.ic_sms)
                }
            }
        }.attach()
    }

    class FragmentsAdapter(activity: AppCompatActivity) :
        androidx.viewpager2.adapter.FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SettingsFragment.newInstance()
                else -> MessagesListFragment.newInstance()
            }
        }

    }
}