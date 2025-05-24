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
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.data.dao.ServerSettingsDao // Added
import me.capcom.smsgateway.data.entities.ServerSettings // Added
import me.capcom.smsgateway.databinding.FragmentSettingsBinding
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.services.AgentService // Added for service status
import me.capcom.smsgateway.modules.connection.ConnectionService
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.localserver.LocalServerService
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.localserver.events.IPReceivedEvent
import me.capcom.smsgateway.modules.orchestrator.OrchestratorService
import me.capcom.smsgateway.ui.dialogs.FirstStartDialogFragment
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val settingsHelper: SettingsHelper by inject()
    private val localServerSettings: LocalServerSettings by inject() // Old setting, may remove later
    private val gatewaySettings: GatewaySettings by inject() // Old setting, may remove later
    private val connectionService: ConnectionService by inject() // Still useful for general connectivity

    private val events: EventBus by inject()

    // Old services, will be replaced by AgentService logic display
    private val localServerSvc: LocalServerService by inject()
    private val gatewaySvc: GatewayService by inject()
    private val orchestratorSvc: OrchestratorService by inject()

    private val serverSettingsDao: ServerSettingsDao by inject() // Added for Agent Status

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(FirstStartDialogFragment.REQUEST_KEY) { _, data ->
            val result = FirstStartDialogFragment.getResult(data)
            when (result) {
                FirstStartDialogFragment.Result.Canceled -> {
                    Toast.makeText(
                        requireContext(),
                        "Operation cancelled",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    binding.buttonStart.isChecked = false
                    return@setFragmentResultListener
                }

                FirstStartDialogFragment.Result.SignUp -> requestPermissionsAndStart()

                FirstStartDialogFragment.Result.SignIn -> {
                    val username = FirstStartDialogFragment.getUsername(data)
                    val password = FirstStartDialogFragment.getPassword(data)
                    lifecycleScope.launch {
                        try {
                            gatewaySvc.registerDevice(
                                null,
                                GatewayService.RegistrationMode.WithCredentials(username, password)
                            )
                            requestPermissionsAndStart()
                        } catch (th: Throwable) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to register device: ${th.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                FirstStartDialogFragment.Result.SignInByCode -> {
                    val code = FirstStartDialogFragment.getCode(data)
                    lifecycleScope.launch {
                        try {
                            gatewaySvc.registerDevice(
                                null,
                                GatewayService.RegistrationMode.WithCode(code)
                            )
                            requestPermissionsAndStart()
                        } catch (th: Throwable) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to register device: ${th.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

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

        binding.switchAutostart.isChecked = settingsHelper.autostart

        binding.switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            settingsHelper.autostart = isChecked
        }
        binding.switchUseRemoteServer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != gatewaySettings.enabled) {
                restartRequiredNotification()
            }

            gatewaySettings.enabled = isChecked
            binding.layoutRemoteServer.isVisible = isChecked
            binding.textConnectionStatus.isVisible = isChecked
        }
        binding.switchUseLocalServer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != localServerSettings.enabled) {
                restartRequiredNotification()
            }

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
            events.collect<DeviceRegisteredEvent.Success> { event ->
                binding.textRemoteAddress.text = getString(R.string.address_is, event.server)

                binding.textRemoteUsername.movementMethod = LinkMovementMethod.getInstance()
                binding.textRemotePassword.movementMethod = LinkMovementMethod.getInstance()

                binding.textRemoteUsername.text = makeCopyableLink(
                    Html
                        .fromHtml(
                            "<a href>${event.login}</a>"
                        )
                )

                binding.textRemotePassword.text = when (event.password) {
                    null -> getString(R.string.n_a)
                    else -> makeCopyableLink(
                        Html
                            .fromHtml(
                                "<a href>${event.password}</a>"
                            )
                    )
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            events.collect<DeviceRegisteredEvent.Failure> { event ->
                binding.textRemoteAddress.text = getString(R.string.address_is, event.server)

                binding.textRemoteUsername.text = getString(R.string.not_registered)
                binding.textRemotePassword.text = getString(R.string.n_a)

                Toast.makeText(
                    requireContext(),
                    getString(R.string.failed_to_register_device, event.reason),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            events.collect<IPReceivedEvent> { event ->
                binding.textLocalUsername.text = makeCopyableLink(
                    Html.fromHtml(
                        "<a href>${localServerSettings.username}</a>"
                    )
                )
                binding.textLocalPassword.text = makeCopyableLink(
                    Html.fromHtml(
                        "<a href>${localServerSettings.password}</a>"
                    )
                )

                binding.textLocalIP.text = event.localIP?.let {
                    makeCopyableLink(
                        Html.fromHtml(
                            getString(
                                R.string.settings_local_address_is,
                                event.localIP,
                                localServerSettings.port
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
                                localServerSettings.port
                            )
                        )
                    )
                } ?: getString(R.string.settings_public_address_not_found)
            }
        }

        stateLiveData.observe(viewLifecycleOwner) {
            binding.buttonStart.isChecked = it
        }

        connectionService.status.observe(viewLifecycleOwner) {
            binding.textConnectionStatus.apply {
                isVisible = binding.switchUseRemoteServer.isChecked // This switch might be repurposed or removed
                isEnabled = it
                text = when (it) {
                    true -> context.getString(R.string.internet_connection_available)
                    false -> context.getString(R.string.internet_connection_unavailable)
                }
            }
        }

        // Observe Agent Server Settings for status display
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            serverSettingsDao.getSettings().collectLatest { settings ->
                updateAgentStatusDisplay(settings)
            }
        }
        // Observe AgentService status (basic running/stopped, more detailed status is complex for MVP)
        // This requires AgentService to expose a LiveData or StateFlow for its status.
        // For MVP, we'll infer from settings for now, or use a simple static boolean if AgentService has one.
        // For a more reactive UI, AgentService would need to provide its operational status.
        // Let's assume AgentService.isRunning can be observed or has a static accessor for MVP.
        // This part is conceptual for MVP bonus. AgentService.isRunning is not implemented yet.
        // For now, the textAgentStatus will primarily reflect registration status.
        // We can add a line about service running state based on the `orchestratorSvc` for now.
        // This can be refined when OrchestratorService is replaced/integrated with AgentService.
    }

    private fun updateAgentStatusDisplay(settings: ServerSettings?) {
        val statusText: String
        var statusColorRes: Int = R.color.agent_status_default_background // Default color

        if (settings == null || settings.serverUrl.isBlank()) {
            statusText = getString(R.string.status_server_not_configured)
            statusColorRes = R.color.agent_status_error_background
        } else if (settings.agentId.isNullOrBlank() || settings.apiKey.isNullOrBlank()) {
            statusText = getString(R.string.status_agent_not_registered, settings.serverUrl)
            statusColorRes = R.color.agent_status_warning_background
        } else {
            // AgentService.isRunning is not directly available here.
            // For now, we'll just show registration status.
            // Later, AgentService could provide a LiveData for its operational status.
            val agentServiceRunning = orchestratorSvc.isServiceRunning(requireContext(), AgentService::class.java) // Example check
            val serviceStatusString = if (agentServiceRunning) getString(R.string.status_service_running) else getString(R.string.status_service_stopped)

            statusText = getString(R.string.status_agent_registered, settings.serverUrl, settings.agentId) + "\n" + serviceStatusString
            statusColorRes = R.color.agent_status_ok_background
            
            // TODO: Check settings.isEnabled from AgentConfigResponse once AgentService updates it in ServerSettings
            // if (settings.isEnabled == false) { // Assuming ServerSettings might get an isEnabled field later
            //    statusText += "\n" + getString(R.string.status_agent_disabled_by_server)
            //    statusColorRes = R.color.agent_status_warning_background
            // }
        }

        binding.textAgentStatus.text = statusText
        binding.textAgentStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), statusColorRes))
        binding.textAgentStatus.isVisible = true
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
            if (gatewaySettings.enabled
                && gatewaySettings.registrationInfo == null
            ) {
                cloudFirstStart()
                return
            }

            requestPermissionsAndStart()
        } else {
            stop()
        }
    }

    private fun cloudFirstStart() {
        FirstStartDialogFragment.newInstance()
            .show(parentFragmentManager, "signin")
    }

    private fun stop() {
        orchestratorSvc.stop(requireContext())
    }

    private fun start() {
        orchestratorSvc.start(requireContext(), false)
    }

    private fun requestPermissionsAndStart() {
        val permissionsRequired =
            listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
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

    private fun restartRequiredNotification() {
        if (this.stateLiveData.value != true) {
            return
        }

        Toast.makeText(
            requireContext(),
            getString(R.string.to_apply_the_changes_restart_the_app_using_the_button_below),
            Toast.LENGTH_SHORT
        ).show()
    }

    private val permissionsRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            // Permission is granted. Continue the action or workflow in your
            // app.
            Log.d(javaClass.name, "Permissions granted")
        } else {
            Toast.makeText(
                requireContext(),
                "Not all permissions granted, some features may not work",
                Toast.LENGTH_SHORT
            )
                .show()
        }

        start()
    }

    private val stateLiveData by lazy {
        object : MediatorLiveData<Boolean>() {
            private var gatewayStatus = false
            private var localServerStatus = false

            init {
            // This LiveData needs to be re-evaluated in context of AgentService.
            // For now, it reflects the old services.
            // A new LiveData, perhaps observing AgentService status, would be better.
            // For MVP, we can keep it as is, or simplify/remove if it's too confusing.
            // Let's assume for now that this existing LiveData for buttonStart state is sufficient.
                addSource(gatewaySvc.isActiveLiveData(requireContext())) {
                    gatewayStatus = it
                value = gatewayStatus || localServerStatus // This logic might change
                }
                addSource(localServerSvc.isActiveLiveData(requireContext())) {
                    localServerStatus = it
                value = gatewayStatus || localServerStatus // This logic might change
                }
            // TODO: Add source for AgentService status if it provides a LiveData for running state.
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