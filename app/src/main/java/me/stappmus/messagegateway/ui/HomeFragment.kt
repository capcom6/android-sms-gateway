package me.stappmus.messagegateway.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.app.role.RoleManager
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.text.toSpanned
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.stappmus.messagegateway.R
import me.stappmus.messagegateway.databinding.FragmentHomeBinding
import me.stappmus.messagegateway.helpers.SettingsHelper
import me.stappmus.messagegateway.modules.connection.ConnectionService
import me.stappmus.messagegateway.modules.events.EventBus
import me.stappmus.messagegateway.modules.gateway.GatewayService
import me.stappmus.messagegateway.modules.gateway.GatewaySettings
import me.stappmus.messagegateway.modules.gateway.events.DeviceRegisteredEvent
import me.stappmus.messagegateway.modules.localserver.LocalServerService
import me.stappmus.messagegateway.modules.localserver.LocalServerSettings
import me.stappmus.messagegateway.modules.localserver.events.IPReceivedEvent
import me.stappmus.messagegateway.modules.orchestrator.OrchestratorService
import me.stappmus.messagegateway.ui.dialogs.FirstStartDialogFragment
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val settingsHelper: SettingsHelper by inject()
    private val localServerSettings: LocalServerSettings by inject()
    private val gatewaySettings: GatewaySettings by inject()
    private val connectionService: ConnectionService by inject()

    private val events: EventBus by inject()

    private val localServerSvc: LocalServerService by inject()
    private val gatewaySvc: GatewayService by inject()

    private val orchestratorSvc: OrchestratorService by inject()

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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textLocalIP.movementMethod = LinkMovementMethod.getInstance()
        binding.textPublicIP.movementMethod = LinkMovementMethod.getInstance()
        binding.textLocalUsername.movementMethod = LinkMovementMethod.getInstance()
        binding.textLocalPassword.movementMethod = LinkMovementMethod.getInstance()
        binding.textLocalDeviceId.movementMethod = LinkMovementMethod.getInstance()
        binding.textRemoteDeviceId.movementMethod = LinkMovementMethod.getInstance()

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

        binding.buttonDisableBatteryOptimization.setOnClickListener {
            openDisableBatteryOptimizationShortcut()
        }
        binding.buttonOpenAppBatterySettings.setOnClickListener {
            openAppBatterySettingsShortcut()
        }
        binding.buttonSetDefaultSmsApp.setOnClickListener {
            openDefaultSmsShortcut()
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

                // Set Cloud Server Device ID
                binding.textRemoteDeviceId.text = gatewaySettings.deviceId?.let {
                    makeCopyableLink(
                        Html.fromHtml(
                            "<a href>$it</a>"
                        )
                    )
                } ?: getString(R.string.n_a)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            events.collect<DeviceRegisteredEvent.Failure> { event ->
                binding.textRemoteAddress.text = getString(R.string.address_is, event.server)

                binding.textRemoteUsername.text = getString(R.string.not_registered)
                binding.textRemotePassword.text = getString(R.string.n_a)

                // Set Cloud Server Device ID (even for failure cases)
                binding.textRemoteDeviceId.text = gatewaySettings.deviceId?.let {
                    makeCopyableLink(
                        Html.fromHtml(
                            "<a href>$it</a>"
                        )
                    )
                } ?: getString(R.string.n_a)

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

                // Set Local Server Device ID
                binding.textLocalDeviceId.text = localServerSettings.deviceId?.let {
                    makeCopyableLink(
                        Html.fromHtml(
                            "<a href>$it</a>"
                        )
                    )
                } ?: getString(R.string.n_a)
            }
        }

        stateLiveData.observe(viewLifecycleOwner) {
            binding.buttonStart.isChecked = it
        }

        connectionService.status.observe(viewLifecycleOwner) {
            binding.textConnectionStatus.apply {
                isVisible = binding.switchUseRemoteServer.isChecked
                isEnabled = it
                text = when (it) {
                    true -> context.getString(R.string.internet_connection_available)
                    false -> context.getString(R.string.internet_connection_unavailable)
                }
            }
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

        gatewaySettings.enabled = false
        binding.switchUseRemoteServer.isChecked = false
        binding.switchUseLocalServer.isChecked = localServerSettings.enabled
    }

    private fun actionStart(start: Boolean) {
        if (start) {
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
                Manifest.permission.RECEIVE_MMS,
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

    private fun openDisableBatteryOptimizationShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            showShortcutUnavailable()
            return
        }

        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            Toast.makeText(requireContext(), getString(R.string.disabled), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        openIntentOrToast(intent)
    }

    private fun openAppBatterySettingsShortcut() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        openIntentOrToast(intent)
    }

    private fun openDefaultSmsShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = requireContext().getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                try {
                    smsRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                } catch (e: Exception) {
                    Log.e(javaClass.name, "Failed to launch SMS role request", e)
                    openIntentOrToast(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
                return
            }
        }

        openIntentOrToast(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    private fun openIntentOrToast(intent: Intent) {
        val packageManager = requireContext().packageManager
        if (intent.resolveActivity(packageManager) == null) {
            showShortcutUnavailable()
            return
        }
        startActivity(intent)
    }

    private fun showShortcutUnavailable() {
        Toast.makeText(
            requireContext(),
            getString(R.string.reliability_shortcut_unavailable),
            Toast.LENGTH_SHORT
        ).show()
    }

    private val smsRoleRequest: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Log.d(javaClass.name, "SMS role granted")
        } else {
            Log.d(javaClass.name, "SMS role denied")
        }
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
