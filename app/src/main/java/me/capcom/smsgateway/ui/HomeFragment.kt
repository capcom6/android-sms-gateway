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
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.FragmentSettingsBinding
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.localserver.LocalServerService
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.localserver.events.IPReceivedEvent
import me.capcom.smsgateway.modules.orchestrator.OrchestratorService
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val settingsHelper: SettingsHelper by inject()
    private val localServerSettings: LocalServerSettings by inject()
    private val gatewaySettings: GatewaySettings by inject()

    private val events: EventBus by inject()

    private val localServerSvc: LocalServerService by inject()
    private val gatewaySvc: GatewayService by inject()

    private val orchestratorSvc: OrchestratorService by inject()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textLocalIP.movementMethod = LinkMovementMethod.getInstance()
        binding.textPublicIP.movementMethod = LinkMovementMethod.getInstance()
        binding.textLocalUsername.movementMethod = LinkMovementMethod.getInstance()
        binding.textLocalPassword.movementMethod = LinkMovementMethod.getInstance()

        binding.textLocalUsername.text = makeCopyableLink(
            Html.fromHtml(
                "<a href>sms</a>"
            )
        )
        binding.textLocalPassword.text = makeCopyableLink(
            Html.fromHtml(
                "<a href>${settingsHelper.serverToken}</a>"
            )
        )

        binding.switchAutostart.isChecked = settingsHelper.autostart

        binding.switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            settingsHelper.autostart = isChecked
        }
        binding.switchUseRemoteServer.setOnCheckedChangeListener { _, isChecked ->
            gatewaySettings.enabled = isChecked
            binding.layoutRemoteServer.isVisible = isChecked
        }
        binding.switchUseLocalServer.setOnCheckedChangeListener { _, isChecked ->
            localServerSettings.enabled = isChecked
            binding.layoutLocalServer.isVisible = isChecked
        }

        binding.buttonStart.setOnClickListener {
            actionStart(binding.buttonStart.isChecked)
        }

//        if (settingsHelper.autostart) {
//            actionStart(true)
//        }

        viewLifecycleOwner.lifecycleScope.launch {
            events.collect<DeviceRegisteredEvent> { event ->
                binding.textRemoteAddress.text = getString(R.string.address_is, event.server)

                binding.textRemoteUsername.movementMethod = LinkMovementMethod.getInstance()
                binding.textRemotePassword.movementMethod = LinkMovementMethod.getInstance()

                binding.textRemoteUsername.text = makeCopyableLink(
                    Html
                        .fromHtml(
                            "<a href>${event.login}</a>"
                        )
                )
                binding.textRemotePassword.text = makeCopyableLink(
                    Html
                        .fromHtml(
                            "<a href>${event.password}</a>"
                        )
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            events.collect<IPReceivedEvent> { event ->
                binding.textLocalIP.text = event.localIP?.let {
                    makeCopyableLink(
                        Html.fromHtml(
                            getString(
                                R.string.settings_local_address_is,
                                event.localIP,
                                settingsHelper.serverPort
                            )
                        )
                    )

                } ?: getString(R.string.settings_local_address_not_found)

                binding.textPublicIP.text = event.publicIP?.let {
                    makeCopyableLink(
                        Html.fromHtml(
                            getString(
                                R.string.settings_public_address_is,
                                event.publicIP,
                                settingsHelper.serverPort
                            )
                        )
                    )
                } ?: getString(R.string.settings_public_address_not_found)
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

        binding.switchUseRemoteServer.isChecked = gatewaySettings.enabled
        binding.switchUseLocalServer.isChecked = localServerSettings.enabled
    }

    private fun actionStart(start: Boolean) {
        if (start) {
            requestPermissionsAndStart()
        } else {
            stop()
        }
    }

    private fun stop() {
        orchestratorSvc.stop(requireContext())
    }

    private fun start() {
        orchestratorSvc.start(requireContext())
    }

    private fun requestPermissionsAndStart() {
        val permissionsRequired =
            listOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.RECEIVE_SMS,
            )
                .filter {
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        it
                    ) != PackageManager.PERMISSION_GRANTED
                }

        if (permissionsRequired.isEmpty()) {
            start()
            return
        }

        permissionsRequest.launch(permissionsRequired.toTypedArray())
    }

    private val permissionsRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            // Permission is granted. Continue the action or workflow in your
            // app.
            Log.d(javaClass.name, "Permissions granted")
        } else {
            Toast.makeText(requireContext(), "Not all permissions granted", Toast.LENGTH_SHORT)
                .show()
        }

        start()
    }

    private val stateLiveData by lazy {
        object : MediatorLiveData<Boolean>() {
            private var gatewayStatus = false
            private var localServerStatus = false

            init {
                addSource(gatewaySvc.isActiveLiveData(requireContext())) {
                    gatewayStatus = it

                    value = gatewayStatus || localServerStatus
                }
                addSource(localServerSvc.isActiveLiveData(requireContext())) {
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
            HomeFragment()
    }
}