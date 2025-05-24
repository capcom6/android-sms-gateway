package me.capcom.smsgateway.services

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import me.capcom.smsgateway.R
import me.capcom.smsgateway.data.dao.QueuedOutgoingSmsDao
import me.capcom.smsgateway.data.dao.ServerSettingsDao
import me.capcom.smsgateway.data.entities.OutgoingSmsStatus
import me.capcom.smsgateway.data.entities.QueuedOutgoingSms
import me.capcom.smsgateway.data.entities.ServerSettings
import me.capcom.smsgateway.data.remote.KtorServerClient
import me.capcom.smsgateway.data.remote.dto.TaskStatusUpdateRequest
import me.capcom.smsgateway.helpers.PhoneHelper // Assuming this will be refactored
import me.capcom.smsgateway.helpers.SimHelper // Placeholder for refactored SIM selection
import me.capcom.smsgateway.modules.localsms.utils.Logger // Assuming Logger is accessible
import me.capcom.smsgateway.ui.MainActivity
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class AgentService : LifecycleService() {

    private val serverSettingsDao: ServerSettingsDao by inject()
    private val queuedOutgoingSmsDao: QueuedOutgoingSmsDao by inject()
    private val ktorServerClient: KtorServerClient by inject()

    private val logger = Logger.get(this.javaClass.simpleName)

    private var agentJob: Job? = null
    private var settings: ServerSettings? = null

    private val smsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || context == null) return

            val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
            val action = intent.action ?: return

            logger.info("Received broadcast: $action for TaskID: $taskId, Result: $resultCode")

            lifecycleScope.launch {
                when (action) {
                    ACTION_SMS_SENT_TASK_PREFIX + taskId -> handleSmsSent(taskId, resultCode)
                    ACTION_SMS_DELIVERED_TASK_PREFIX + taskId -> handleSmsDelivered(taskId, resultCode)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        logger.info("AgentService creating")
        createNotificationChannel()
        registerSmsStatusReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        logger.info("AgentService onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startForegroundService()
                if (agentJob == null || agentJob?.isActive == false) {
                    startAgentLogic()
                }
            }
            ACTION_STOP -> {
                stopAgentLogic()
                stopSelf()
            }
            ACTION_REPORT_INCOMING_SMS -> {
                val smsJson = intent.getStringExtra(EXTRA_INCOMING_SMS_REQUEST)
                val incomingSmsRequest = me.capcom.smsgateway.receivers.SmsReceiver.fromJson(smsJson) // Use companion from SmsReceiver
                if (incomingSmsRequest != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (settings?.agentId == null || settings?.apiKey == null) {
                            logger.warn("Agent not registered, cannot report incoming SMS.")
                            // Optionally, queue this report until agent is registered.
                            return@launch
                        }
                        logger.info("Reporting incoming SMS from ${incomingSmsRequest.sender} to server.")
                        val success = ktorServerClient.reportIncomingSms(incomingSmsRequest)
                        if (success) {
                            logger.info("Successfully reported incoming SMS from ${incomingSmsRequest.sender}.")
                        } else {
                            logger.warn("Failed to report incoming SMS from ${incomingSmsRequest.sender}.")
                            // TODO: Implement retry logic for reporting incoming SMS, possibly by saving to a local queue.
                        }
                    }
                } else {
                    logger.error("Could not parse IncomingSmsRequest from intent extra.")
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.agent_notification_title))
            .setContentText(getString(R.string.agent_notification_text))
            .setSmallIcon(R.drawable.ic_notification_icon) // Replace with your app's icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        logger.info("AgentService started in foreground.")
    }

    private fun startAgentLogic() {
        agentJob?.cancel() // Cancel any existing job
        agentJob = lifecycleScope.launch(Dispatchers.IO) {
            logger.info("Agent logic started.")
            initializeAgent() // First, ensure agent is registered

            if (settings?.agentId == null || settings?.apiKey == null) {
                logger.error("Agent not registered or API key missing. Stopping agent logic.")
                // Optionally stop service or keep retrying registration
                return@launch
            }

            // Periodic tasks loop
            while (isActive) {
                try {
                    logger.debug("Running periodic tasks...")
                    sendHeartbeat()
                    fetchAgentConfig() // Also updates local `settings` for isEnabled check

                    if (settings?.isEnabled == false) {
                        logger.warn("Agent ${settings?.agentId} is disabled by server. Pausing task processing.")
                        delay(DISABLED_POLL_INTERVAL_MS) // Check config less frequently if disabled
                        continue
                    }

                    fetchAndQueueSmsTasks()
                    processSmsSendQueue()

                    delay(DEFAULT_POLL_INTERVAL_MS)
                } catch (e: CancellationException) {
                    logger.info("Agent logic coroutine cancelled.")
                    break
                } catch (e: Exception) {
                    logger.error("Error in agent logic loop: ${e.message}", e)
                    delay(ERROR_RETRY_INTERVAL_MS) // Wait a bit longer after an error
                }
            }
        }
        logger.info("AgentJob launched.")
    }

    private suspend fun initializeAgent() {
        settings = serverSettingsDao.getSettingsDirect()
        if (settings == null || settings!!.serverUrl.isBlank()) {
            logger.error("Server URL not configured. Agent cannot start.")
            // TODO: Notify UI or user to configure the server URL
            stopAgentLogic() // Stop if not configured
            return
        }

        if (settings!!.agentId.isNullOrBlank() || settings!!.apiKey.isNullOrBlank()) {
            logger.info("Agent ID or API Key is missing. Attempting to register agent...")
            var registrationAttempt = 0
            while (registrationAttempt < MAX_REGISTRATION_ATTEMPTS && (settings!!.agentId.isNullOrBlank() || settings!!.apiKey.isNullOrBlank())) {
                registrationAttempt++
                logger.info("Registration attempt $registrationAttempt / $MAX_REGISTRATION_ATTEMPTS")
                try {
                    val deviceName = Build.MODEL ?: "UnknownDevice"
                    val response = ktorServerClient.registerAgent(deviceName)
                    if (response != null) {
                        settings = settings!!.copy(agentId = response.id, apiKey = response.apiKey)
                        serverSettingsDao.insert(settings!!)
                        logger.info("Agent registered successfully: ID=${response.id}")
                        break // Exit loop on success
                    } else {
                        logger.warn("Agent registration failed (response null).")
                    }
                } catch (e: Exception) {
                    logger.error("Error during agent registration: ${e.message}", e)
                }
                if (settings!!.agentId.isNullOrBlank() || settings!!.apiKey.isNullOrBlank()) {
                     val delayMs = registrazioneBackoff(registrationAttempt)
                     logger.info("Waiting ${delayMs}ms before next registration attempt.")
                     delay(delayMs)
                }
            }
            if (settings!!.agentId.isNullOrBlank() || settings!!.apiKey.isNullOrBlank()) {
                logger.error("Failed to register agent after $MAX_REGISTRATION_ATTEMPTS attempts. Stopping.")
                stopAgentLogic()
            }
        } else {
            logger.info("Agent already registered: ID=${settings!!.agentId}")
        }
    }
    
    private fun registrazioneBackoff(attempt: Int): Long {
        return TimeUnit.SECONDS.toMillis(minOf(600, (2.0.pow(attempt -1) * 5).toLong())) // Exponential backoff: 5s, 10s, 20s... max 10min
    }


    private suspend fun sendHeartbeat() {
        if (ktorServerClient.sendHeartbeat()) {
            logger.info("Heartbeat sent successfully.")
        } else {
            logger.warn("Failed to send heartbeat.")
        }
    }

    private suspend fun fetchAgentConfig() {
        val config = ktorServerClient.fetchAgentConfig()
        if (config != null) {
            logger.info("Fetched agent config: Name=${config.name}, Enabled=${config.isEnabled}, Limit=${config.dailySmsLimit}")
            // Update local settings if changed (e.g., isEnabled status)
            // Note: AgentService currently uses its own `settings` variable.
            // For a more robust solution, ServerSettingsDao could be updated here,
            // and then other parts of the app could observe changes from the DAO.
            // For MVP, just updating the local `settings` variable for `isEnabled` check.
            settings = settings?.copy(isEnabled = config.isEnabled) // Update local copy
        } else {
            logger.warn("Failed to fetch agent config.")
        }
    }

    private suspend fun fetchAndQueueSmsTasks() {
        val tasks = ktorServerClient.fetchOutgoingSmsTasks()
        if (tasks != null) {
            if (tasks.isNotEmpty()) logger.info("Fetched ${tasks.size} new SMS tasks from server.")
            for (taskDto in tasks) {
                val existingTask = queuedOutgoingSmsDao.getByTaskId(taskDto.taskId)
                if (existingTask == null) {
                    val newTask = QueuedOutgoingSms(
                        taskId = taskDto.taskId,
                        recipient = taskDto.recipient,
                        messageContent = taskDto.messageText,
                        status = OutgoingSmsStatus.PENDING, // Or PENDING_SEND
                        createdAt = System.currentTimeMillis(), // Or use a server-provided timestamp if available
                        nextSendAttemptAt = System.currentTimeMillis()
                    )
                    queuedOutgoingSmsDao.insert(newTask)
                    logger.info("Queued new task: ${newTask.taskId} for ${newTask.recipient}")
                } else {
                    logger.debug("Task ${taskDto.taskId} already exists in local queue.")
                }
            }
        } else {
            logger.warn("Failed to fetch outgoing SMS tasks.")
        }
    }

    private suspend fun processSmsSendQueue() {
        val pendingMessages = queuedOutgoingSmsDao.getPendingMessages().filter { it.nextSendAttemptAt <= System.currentTimeMillis() }
        if (pendingMessages.isEmpty()) return

        logger.info("Processing ${pendingMessages.size} messages from send queue.")
        for (sms in pendingMessages) {
            if (settings?.isEnabled == false) {
                logger.warn("Agent ${settings?.agentId} is disabled by server. Skipping sending task ${sms.taskId}.")
                continue // Skip sending if agent is disabled
            }
            
            logger.info("Attempting to send SMS for task ${sms.taskId} to ${sms.recipient}")
            // TODO: Implement SIM selection based on a (future) setting or round-robin from available SIMs
            val selectedSimSlotIndex: Int? = SimHelper.getDefaultSimSlotIndex(this) // Placeholder

            // Mark as SENDING (or another intermediate status)
            // sms.status = OutgoingSmsStatus.SENDING // Need a new status or handle this carefully
            // queuedOutgoingSmsDao.update(sms) // Not using SENDING state for now to simplify flow

            try {
                // Refactored SMS sending logic
                SmsHelper.sendSms(
                    context = this,
                    simSlotIndex = selectedSimSlotIndex,
                    phoneNumber = sms.recipient,
                    message = sms.messageContent,
                    taskId = sms.taskId
                )
                logger.info("SMS for task ${sms.taskId} handed off to SmsManager.")
                // Status will be updated by the BroadcastReceiver for sent/delivered intents
            } catch (e: Exception) {
                logger.error("Failed to initiate sending for task ${sms.taskId}: ${e.message}", e)
                sms.status = OutgoingSmsStatus.FAILED
                sms.retries += 1
                if (sms.retries >= MAX_SEND_RETRIES) {
                    logger.warn("Max retries reached for task ${sms.taskId}. Reporting FAILED to server.")
                    ktorServerClient.updateTaskStatus(sms.taskId, TaskStatusUpdateRequest("FAILED", "Max send retries or send error: ${e.message}"))
                } else {
                    sms.nextSendAttemptAt = System.currentTimeMillis() + sendRetryBackoff(sms.retries)
                    logger.info("Scheduled retry ${sms.retries} for task ${sms.taskId} at ${sms.nextSendAttemptAt}")
                }
                queuedOutgoingSmsDao.update(sms)
            }
        }
    }
    
    private fun sendRetryBackoff(attempt: Int): Long {
        return TimeUnit.MINUTES.toMillis(minOf(30, (2.0.pow(attempt -1)).toLong())) // Exponential backoff: 1m, 2m, 4m... max 30min
    }


    private suspend fun handleSmsSent(taskId: String, resultCode: Int) {
        val sms = queuedOutgoingSmsDao.getByTaskId(taskId) ?: return
        logger.info("Handling SMS_SENT for TaskID: $taskId, ResultCode: $resultCode")

        if (resultCode == Activity.RESULT_OK) {
            sms.status = OutgoingSmsStatus.SENT // Or a status like SENT_AWAITING_SERVER_ACK
            queuedOutgoingSmsDao.update(sms)
            logger.info("Task $taskId marked as SENT locally. Attempting to update server.")
            val serverUpdateSuccess = ktorServerClient.updateTaskStatus(taskId, TaskStatusUpdateRequest("SENT"))
            if (!serverUpdateSuccess) {
                logger.warn("Failed to update server for SENT task $taskId. Will retry server update later.")
                // TODO: Implement retry for server ACK, or mark as FAILED_SERVER_ACK
            } else {
                logger.info("Server successfully updated for SENT task $taskId.")
            }
        } else {
            sms.status = OutgoingSmsStatus.FAILED
            sms.retries += 1
            val failureReason = "SMS send failed: ${SmsHelper.resultToErrorMessage(resultCode)}"
            logger.warn("SMS send failed for task $taskId. Reason: $failureReason. Retries: ${sms.retries}")

            if (sms.retries >= MAX_SEND_RETRIES) {
                logger.warn("Max retries reached for task ${sms.taskId} after send failure. Reporting FAILED to server.")
                ktorServerClient.updateTaskStatus(taskId, TaskStatusUpdateRequest("FAILED", failureReason))
            } else {
                sms.nextSendAttemptAt = System.currentTimeMillis() + sendRetryBackoff(sms.retries)
                logger.info("Scheduled retry ${sms.retries} for task ${sms.taskId} at ${sms.nextSendAttemptAt} due to send failure.")
            }
            queuedOutgoingSmsDao.update(sms)
        }
    }

    private suspend fun handleSmsDelivered(taskId: String, resultCode: Int) {
        val sms = queuedOutgoingSmsDao.getByTaskId(taskId) ?: return
        logger.info("Handling SMS_DELIVERED for TaskID: $taskId, ResultCode: $resultCode")

        if (resultCode == Activity.RESULT_OK) {
            sms.status = OutgoingSmsStatus.CONFIRMED_DELIVERED // Or a status like DELIVERED_AWAITING_SERVER_ACK
            logger.info("Task $taskId confirmed DELIVERED locally.")
            // Optional: Report "DELIVERED" status to server if your API supports it
            // ktorServerClient.updateTaskStatus(taskId, TaskStatusUpdateRequest("DELIVERED"))
        } else {
            // This case is less common for delivery reports but handle defensively
            sms.status = OutgoingSmsStatus.CONFIRMED_FAILED // Or FAILED if delivery confirmation indicates failure
            logger.warn("Delivery report for task $taskId indicates failure or non-OK result: $resultCode.")
             // Optional: Report "FAILED" or a specific delivery failure status to server
            // ktorServerClient.updateTaskStatus(taskId, TaskStatusUpdateRequest("FAILED", "Delivery failed: $resultCode"))
        }
        queuedOutgoingSmsDao.update(sms)
    }


    private fun stopAgentLogic() {
        logger.info("Stopping agent logic...")
        agentJob?.cancel()
        agentJob = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Agent Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
    
    private fun registerSmsStatusReceiver() {
        val intentFilter = IntentFilter().apply {
            // Add actions dynamically based on a prefix. This is tricky because the full action string is needed.
            // For now, we'll have to rely on the receiver catching actions that *start with* the prefix,
            // and then parse the taskId from the action string itself if possible, or from extras.
            // A better approach for dynamic actions is to register for specific task IDs as they are sent,
            // but that makes the receiver management more complex.
            // For MVP, let's assume the receiver gets all actions and filters internally.
            // This is not ideal. We will register for a generic action and parse task ID from extras.
            // A better way is to have two fixed actions, and pass TaskID as an extra.
            // Let's redefine the actions for the PendingIntents to be fixed.
            addAction(ACTION_SMS_SENT)     // Generic SENT action
            addAction(ACTION_SMS_DELIVERED) // Generic DELIVERED action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsStatusReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(smsStatusReceiver, intentFilter)
        }
        logger.info("SmsStatusReceiver registered.")
    }


    override fun onDestroy() {
        super.onDestroy()
        logger.info("AgentService destroying")
        stopAgentLogic()
        unregisterReceiver(smsStatusReceiver)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null // Not a bound service
    }

    companion object {
        private const val CHANNEL_ID = "AgentServiceChannel"
        private const val NOTIFICATION_ID = 101
        const val ACTION_START = "me.capcom.smsgateway.services.AgentService.ACTION_START"
        const val ACTION_STOP = "me.capcom.smsgateway.services.AgentService.ACTION_STOP"
        const val ACTION_REPORT_INCOMING_SMS = "me.capcom.smsgateway.services.AgentService.ACTION_REPORT_INCOMING_SMS" // New
        
        // Actions for BroadcastReceiver
        const val ACTION_SMS_SENT = "me.capcom.smsgateway.SMS_SENT_ACTION"
        const val ACTION_SMS_DELIVERED = "me.capcom.smsgateway.SMS_DELIVERED_ACTION"
        const val EXTRA_TASK_ID = "me.capcom.smsgateway.EXTRA_TASK_ID"
        const val EXTRA_INCOMING_SMS_REQUEST = "me.capcom.smsgateway.EXTRA_INCOMING_SMS_REQUEST" // New

        // Removed old TASK_PREFIX constants

        private const val DEFAULT_POLL_INTERVAL_MS = 15_000L // 15 seconds
        private const val DISABLED_POLL_INTERVAL_MS = 300_000L // 5 minutes
        private const val ERROR_RETRY_INTERVAL_MS = 60_000L // 1 minute
        private const val MAX_REGISTRATION_ATTEMPTS = 10
        private const val MAX_SEND_RETRIES = 3
    }
}

// Placeholder for refactored SMS sending logic
object SmsHelper {
    @SuppressLint("MissingPermission") // Permissions should be checked before calling
    fun sendSms(context: Context, simSlotIndex: Int?, phoneNumber: String, message: String, taskId: String) {
        // Not using sentPIActionPrefix and deliveredPIActionPrefix directly, fixed actions are used.
        val smsManager = getSmsManager(context, simSlotIndex)
        val countryCode = (context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager)?.networkCountryIso

        val sentIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(), // Use taskId hash for unique request code
            Intent(AgentService.ACTION_SMS_SENT).putExtra(AgentService.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val deliveredIntent = PendingIntent.getBroadcast(
            context,
            // Ensure unique request code for deliveredIntent, different from sentIntent
            // Adding a constant or using a different hashing approach if taskId.hashCode() could collide.
            // For simplicity, adding 1 is usually sufficient if taskIds are diverse.
            (taskId.hashCode() + 1), 
            Intent(AgentService.ACTION_SMS_DELIVERED).putExtra(AgentService.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val parts = smsManager.divideMessage(message)
        val normalizedPhoneNumber = PhoneHelper.filterPhoneNumber(phoneNumber, countryCode)

        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(
                normalizedPhoneNumber,
                null, // Service center
                parts,
                ArrayList(parts.map { sentIntent }),
                ArrayList(parts.map { deliveredIntent })
            )
        } else {
            smsManager.sendTextMessage(
                normalizedPhoneNumber,
                null, // Service center
                message,
                sentIntent,
                deliveredIntent
            )
        }
    }

    @SuppressLint("NewApi") // Suppress for getSystemService with class
    private fun getSmsManager(context: Context, simSlotIndex: Int?): SmsManager {
        // Logic adapted from old MessagesService.getSmsManager
        // Requires READ_PHONE_STATE for SIM selection. Ensure it's requested.
        // For MVP, this can be simplified or assume permission is granted.
        
        if (simSlotIndex != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
            if (subscriptionManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // On API 33+, READ_PHONE_NUMBERS might be needed depending on exact subscription info access
                // However, if we have slotIndex, we might get SubscriptionInfo without sensitive phone numbers.
                // This part needs careful permission handling in a real app.
                // For now, log and fallback if permission is missing for specific selection.
                Logger.get("SmsHelper").warn("READ_PHONE_STATE permission not granted for SIM selection. Falling back to default SMS manager.")
                return SmsManager.getDefault()
            }

            val subInfoList = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            val subInfo = subInfoList.find { it.simSlotIndex == simSlotIndex }

            if (subInfo != null) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subInfo.subscriptionId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subInfo.subscriptionId)
                }
            } else {
                Logger.get("SmsHelper").warn("Subscription info not found for SIM slot $simSlotIndex. Falling back to default.")
                return SmsManager.getDefault()
            }
        }
        return SmsManager.getDefault()
    }
    
    // Full resultToErrorMessage from old MessagesService
    fun resultToErrorMessage(resultCode: Int): String {
        return when (resultCode) {
            SmsManager.RESULT_ERROR_NONE -> "RESULT_ERROR_NONE (No error)"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "RESULT_ERROR_GENERIC_FAILURE (Generic failure cause)"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "RESULT_ERROR_RADIO_OFF (Failed because radio was explicitly turned off)"
            SmsManager.RESULT_ERROR_NULL_PDU -> "RESULT_ERROR_NULL_PDU (Failed because no PDU provided)"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "RESULT_ERROR_NO_SERVICE (Failed because service is currently unavailable)"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "RESULT_ERROR_LIMIT_EXCEEDED (Failed because we reached the sending queue limit)"
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "RESULT_ERROR_FDN_CHECK_FAILURE (Failed because FDN is enabled)"
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NOT_ALLOWED (Failed because user denied the sending of this short code)"
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED (Failed because the user has denied this app ever send premium short codes)"
            SmsManager.RESULT_RADIO_NOT_AVAILABLE -> "RESULT_RADIO_NOT_AVAILABLE (Failed because the radio was not available)"
            SmsManager.RESULT_NETWORK_REJECT -> "RESULT_NETWORK_REJECT (Failed because of network rejection)"
            SmsManager.RESULT_INVALID_ARGUMENTS -> "RESULT_INVALID_ARGUMENTS (Failed because of invalid arguments)"
            SmsManager.RESULT_INVALID_STATE -> "RESULT_INVALID_STATE (Failed because of an invalid state)"
            SmsManager.RESULT_NO_MEMORY -> "RESULT_NO_MEMORY (Failed because there is no memory)"
            SmsManager.RESULT_INVALID_SMS_FORMAT -> "RESULT_INVALID_SMS_FORMAT (Failed because the SMS format is not valid)"
            SmsManager.RESULT_SYSTEM_ERROR -> "RESULT_SYSTEM_ERROR (Failed because of a system error)"
            SmsManager.RESULT_MODEM_ERROR -> "RESULT_MODEM_ERROR (Failed because of a modem error)"
            SmsManager.RESULT_NETWORK_ERROR -> "RESULT_NETWORK_ERROR (Failed because of a network error)"
            SmsManager.RESULT_ENCODING_ERROR -> "RESULT_ENCODING_ERROR (Failed because of an encoding error)"
            SmsManager.RESULT_INVALID_SMSC_ADDRESS -> "RESULT_INVALID_SMSC_ADDRESS (Failed because of an invalid SMSC address)"
            SmsManager.RESULT_OPERATION_NOT_ALLOWED -> "RESULT_OPERATION_NOT_ALLOWED (Failed because the operation is not allowed)"
            SmsManager.RESULT_INTERNAL_ERROR -> "RESULT_INTERNAL_ERROR (Failed because of an internal error)"
            SmsManager.RESULT_NO_RESOURCES -> "RESULT_NO_RESOURCES (Failed because there are no resources)"
            SmsManager.RESULT_CANCELLED -> "RESULT_CANCELLED (Failed because the operation was cancelled)"
            SmsManager.RESULT_REQUEST_NOT_SUPPORTED -> "RESULT_REQUEST_NOT_SUPPORTED (Failed because the request is not supported)"
            SmsManager.RESULT_NO_BLUETOOTH_SERVICE -> "RESULT_NO_BLUETOOTH_SERVICE (Failed sending via Bluetooth because the Bluetooth service is not available)"
            SmsManager.RESULT_INVALID_BLUETOOTH_ADDRESS -> "RESULT_INVALID_BLUETOOTH_ADDRESS (Failed sending via Bluetooth because the Bluetooth device address is invalid)"
            SmsManager.RESULT_BLUETOOTH_DISCONNECTED -> "RESULT_BLUETOOTH_DISCONNECTED (Failed sending via Bluetooth because Bluetooth disconnected)"
            SmsManager.RESULT_UNEXPECTED_EVENT_STOP_SENDING -> "RESULT_UNEXPECTED_EVENT_STOP_SENDING (Failed because the user denied or canceled the dialog displayed for a premium shortcode SMS or rate-limited SMS)"
            SmsManager.RESULT_SMS_BLOCKED_DURING_EMERGENCY -> "RESULT_SMS_BLOCKED_DURING_EMERGENCY (Failed sending during an emergency call)"
            SmsManager.RESULT_SMS_SEND_RETRY_FAILED -> "RESULT_SMS_SEND_RETRY_FAILED (Failed to send an SMS retry)"
            SmsManager.RESULT_REMOTE_EXCEPTION -> "RESULT_REMOTE_EXCEPTION (Set by BroadcastReceiver to indicate a remote exception while handling a message)"
            SmsManager.RESULT_NO_DEFAULT_SMS_APP -> "RESULT_NO_DEFAULT_SMS_APP (Set by BroadcastReceiver to indicate there's no default SMS app)"
            SmsManager.RESULT_RIL_RADIO_NOT_AVAILABLE -> "RESULT_RIL_RADIO_NOT_AVAILABLE (The radio did not start or is resetting)"
            SmsManager.RESULT_RIL_SMS_SEND_FAIL_RETRY -> "RESULT_RIL_SMS_SEND_FAIL_RETRY (The radio failed to send the SMS and needs to retry)"
            SmsManager.RESULT_RIL_NETWORK_REJECT -> "RESULT_RIL_NETWORK_REJECT (The SMS request was rejected by the network)"
            SmsManager.RESULT_RIL_INVALID_STATE -> "RESULT_RIL_INVALID_STATE (The radio returned an unexpected request for the current state)"
            SmsManager.RESULT_RIL_INVALID_ARGUMENTS -> "RESULT_RIL_INVALID_ARGUMENTS (The radio received invalid arguments in the request)"
            SmsManager.RESULT_RIL_NO_MEMORY -> "RESULT_RIL_NO_MEMORY (The radio didn't have sufficient memory to process the request)"
            SmsManager.RESULT_RIL_REQUEST_RATE_LIMITED -> "RESULT_RIL_REQUEST_RATE_LIMITED (The radio denied the operation due to overly-frequent requests)"
            SmsManager.RESULT_RIL_INVALID_SMS_FORMAT -> "RESULT_RIL_INVALID_SMS_FORMAT (The radio returned an error indicating invalid SMS format)"
            SmsManager.RESULT_RIL_SYSTEM_ERR -> "RESULT_RIL_SYSTEM_ERR (The radio encountered a platform or system error)"
            SmsManager.RESULT_RIL_ENCODING_ERR -> "RESULT_RIL_ENCODING_ERR (The SMS message was not encoded properly)"
            SmsManager.RESULT_RIL_INVALID_SMSC_ADDRESS -> "RESULT_RIL_INVALID_SMSC_ADDRESS (The specified SMSC address was invalid)"
            SmsManager.RESULT_RIL_MODEM_ERR -> "RESULT_RIL_MODEM_ERR (The vendor RIL received an unexpected or incorrect response)"
            SmsManager.RESULT_RIL_NETWORK_ERR -> "RESULT_RIL_NETWORK_ERR (The radio received an error from the network)"
            SmsManager.RESULT_RIL_INTERNAL_ERR -> "RESULT_RIL_INTERNAL_ERR (The modem encountered an unexpected error scenario while handling the request)"
            SmsManager.RESULT_RIL_REQUEST_NOT_SUPPORTED -> "RESULT_RIL_REQUEST_NOT_SUPPORTED (The request was not supported by the radio)"
            SmsManager.RESULT_RIL_INVALID_MODEM_STATE -> "RESULT_RIL_INVALID_MODEM_STATE (The radio cannot process the request in the current modem state)"
            SmsManager.RESULT_RIL_NETWORK_NOT_READY -> "RESULT_RIL_NETWORK_NOT_READY (The network is not ready to perform the request)"
            SmsManager.RESULT_RIL_OPERATION_NOT_ALLOWED -> "RESULT_RIL_OPERATION_NOT_ALLOWED (The radio reports the request is not allowed)"
            SmsManager.RESULT_RIL_NO_RESOURCES -> "RESULT_RIL_NO_RESOURCES (There are insufficient resources to process the request)"
            SmsManager.RESULT_RIL_CANCELLED -> "RESULT_RIL_CANCELLED (The request has been cancelled)"
            SmsManager.RESULT_RIL_SIM_ABSENT -> "RESULT_RIL_SIM_ABSENT (The radio failed to set the location where the CDMA subscription can be retrieved because the SIM or RUIM is absent)"
            SmsManager.RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED -> "RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED (1X voice and SMS are not allowed simultaneously)"
            SmsManager.RESULT_RIL_ACCESS_BARRED -> "RESULT_RIL_ACCESS_BARRED (Access is barred)"
            SmsManager.RESULT_RIL_BLOCKED_DUE_TO_CALL -> "RESULT_RIL_BLOCKED_DUE_TO_CALL (SMS is blocked due to call control, e.g., resource unavailable in the SMR entity)"
            SmsManager.RESULT_RIL_GENERIC_ERROR -> "RESULT_RIL_GENERIC_ERROR (A RIL error occurred during the SMS send)"
            else -> "Unknown error code: $resultCode."
        }
    }
}

// Placeholder for SimHelper, assuming it provides methods to get default or specific SIM info
object SimHelper {
    fun getDefaultSimSlotIndex(context: Context): Int? {
        // For MVP, always use default SIM. Return null for SmsManager.getDefault().
        // More complex logic can be added later if multi-SIM selection is required.
        // If a specific SIM is needed, this method should be updated to return its slot index (0-based).
        return null
    }

    @SuppressLint("MissingPermission") // Permissions should be checked before calling
    fun getSubscriptionIdForSlot(context: Context, slotIndex: Int, subscriptionManager: android.telephony.SubscriptionManager): Int {
        // Requires READ_PHONE_STATE typically.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subInfoList = subscriptionManager.activeSubscriptionInfoList ?: emptyList()
            val subInfo = subInfoList.find { it.simSlotIndex == slotIndex }
            if (subInfo != null) {
                return subInfo.subscriptionId
            }
        }
        return android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }
}
