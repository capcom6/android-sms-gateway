package me.capcom.smsgateway.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.text.toSpanned
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.capcom.smsgateway.App
import me.capcom.smsgateway.R
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

        binding.textAuthentication.movementMethod = LinkMovementMethod.getInstance()
        binding.textAuthentication.text = makeCopyableLink(
            Html
                .fromHtml(
                    getString(
                        R.string.settings_basic_auth,
                        "sms",
                        settingsHelper.serverToken
                    )
                )
        )

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

                binding.textRemoteAuth.movementMethod = LinkMovementMethod.getInstance()
                binding.textRemoteAuth.text = makeCopyableLink(
                    Html
                        .fromHtml(
                            getString(
                                R.string.settings_basic_auth,
                                event.login,
                                event.password
                            )
                        )
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            App.instance.localServerModule.events.events.collect { event ->
                val event = event as? IPReceivedEvent ?: return@collect

                binding.textLocalIP.text =
                    getString(
                        R.string.settings_local_address_is,
                        event.localIP,
                        settingsHelper.serverPort
                    )
                binding.textPublicIP.text =
                    getString(
                        R.string.settings_public_address_is,
                        event.publicIP,
                        settingsHelper.serverPort
                    )
            }
        }

        stateLiveData.observe(viewLifecycleOwner) {
            binding.buttonStart.isChecked = it
        }
    }

    private fun makeCopyableLink(source: Spanned): Spanned {
        val builder = SpannableStringBuilder(source)
        val spans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in spans) {
            val innerText = builder.subSequence(
                builder.getSpanStart(span),
                builder.getSpanEnd(span)
            ).toString()
            val clickableSpan = object : ClickableSpan() {

                override fun onClick(widget: View) {
                    val clipboard = requireContext().getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("", innerText))
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT)
                            .show()
                }
            }
            builder.setSpan(
                clickableSpan,
                builder.getSpanStart(span),
                builder.getSpanEnd(span),
                builder.getSpanFlags(span)
            )
            builder.removeSpan(span)
        }

        return builder.toSpanned()
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

    private val stateLiveData by lazy {
        object : MediatorLiveData<Boolean>() {
            private var gatewayStatus = false
            private var localServerStatus = false

            init {
                addSource(App.instance.gatewayModule.isActiveLiveData(requireContext())) {
                    gatewayStatus = it

                    value = gatewayStatus || localServerStatus
                }
                addSource(App.instance.localServerModule.isActiveLiveData(requireContext())) {
                    localServerStatus = it

                    value = gatewayStatus || localServerStatus
                }
            }
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