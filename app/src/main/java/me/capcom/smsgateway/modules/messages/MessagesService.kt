package me.capcom.smsgateway.modules.messages

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.helpers.PhoneHelper
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.health.domain.CheckResult
import me.capcom.smsgateway.modules.health.domain.Status
import me.capcom.smsgateway.data.dao.VirtualPhoneConfigDao
import me.capcom.smsgateway.data.entities.VirtualPhoneConfig
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.messages.data.SendRequest
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import me.capcom.smsgateway.modules.messages.workers.DailySmsCountResetWorker
import me.capcom.smsgateway.modules.messages.workers.LogTruncateWorker
import me.capcom.smsgateway.modules.messages.workers.SendMessagesWorker
import me.capcom.smsgateway.modules.smpp.SmppService
import me.capcom.smsgateway.receivers.EventsReceiver
import java.util.Date
import java.util.UUID

class MessagesService(
    private val context: Context,
    private val settings: MessagesSettings,
    private val dao: MessagesDao,    // todo: use MessagesRepository
    private val virtualPhoneConfigDao: VirtualPhoneConfigDao, // Added
    private val smppService: SmppService, // Added
    private val encryptionService: EncryptionService,
    private val events: EventBus,
    private val logsService: LogsService,
) {

    private val countryCode: String? =
        (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkCountryIso

    fun healthCheck(): Map<String, CheckResult> {
        val timestamp = System.currentTimeMillis() - 3600 * 1000L
        val failedStats = dao.countFailedFrom(timestamp)
        val processedStats = dao.countProcessedFrom(timestamp)
        return mapOf(
            "failed" to CheckResult(
                when {
                    failedStats.count > 0 && processedStats.count == 0 -> Status.FAIL
                    failedStats.count > 0 -> Status.WARN
                    else -> Status.PASS
                },
                failedStats.count.toLong(),
                "messages",
                "Failed messages for last hour"
            )
        )
    }

    fun start(context: Context) {
        SendMessagesWorker.start(context, false)
        LogTruncateWorker.start(context)
        DailySmsCountResetWorker.start(context) // Added
    }

    fun stop(context: Context) {
        LogTruncateWorker.stop(context)
        SendMessagesWorker.stop(context)
        DailySmsCountResetWorker.stop(context) // Added
    }

    fun enqueueMessage(request: SendRequest) {
        if (getMessage(request.message.id) != null) {
            Log.d(this.javaClass.name, "Message (outgoing) already exists: ${request.message.id}")
            return
        }

        val message = MessageWithRecipients(
            Message(
                request.message.id,
                request.message.text,
                request.params.withDeliveryReport,
                request.params.simNumber,
                request.params.validUntil,
                request.message.isEncrypted,
                request.params.skipPhoneValidation,
                request.params.priority ?: Message.PRIORITY_DEFAULT,
                request.source,

                createdAt = request.message.createdAt.time,
                virtualPhoneId = request.message.virtualPhoneId // Added
            ),
            request.message.phoneNumbers.map {
                MessageRecipient(
                    request.message.id,
                    it,
                    ProcessingState.Pending
                )
            },
        )

        dao.insert(message)

        SendMessagesWorker.start(context, message.message.priority >= Message.PRIORITY_EXPEDITED)
    }

    fun getMessage(id: String): MessageWithRecipients? {
        val message = dao.get(id)
            ?: return null

        val state = message.state

        if (state == message.message.state) {
            return message
        }

        if (state != message.message.state) {
            when (state) {
                ProcessingState.Processed -> dao.setMessageProcessed(message.message.id)
                else -> dao.updateMessageState(message.message.id, state)
            }
        }

        return dao.get(id)
    }

    suspend fun processStateIntent(intent: Intent, resultCode: Int) {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "Status intent received with action ${intent.action} and result code $resultCode",
            mapOf(
                "data" to intent.dataString,
                "uri" to intent.extras?.getString("uri"),
                "pdu" to intent.extras?.getByteArray("pdu")?.joinToString("") { "%02x".format(it) },
            )
        )
        val (state, error) = when (intent.action) {
            EventsReceiver.ACTION_SENT -> when {
                resultCode != Activity.RESULT_OK -> ProcessingState.Failed to "Send result: " + this.resultToErrorMessage(
                    resultCode
                )

                intent.hasExtra("uri") -> ProcessingState.Sent to null
                else -> return
            }

            EventsReceiver.ACTION_DELIVERED -> when (resultCode) {
                Activity.RESULT_OK -> {
                    val message = SmsMessage.createFromPdu(
                        intent.extras?.getByteArray("pdu")
                    )
                    when {
                        message.status.toUInt() < 0b0100000u -> ProcessingState.Delivered to message.status.takeIf { it > 0 }
                            ?.let { "Delivery result from SC ${message.serviceCenterAddress}: ${message.status}" }

                        message.status.toUInt() < 0b1000000u -> return // SC will make more attempts
                        else -> ProcessingState.Failed to "Delivery result from SC ${message.serviceCenterAddress}: ${message.status}"
                    }
                }

                else -> ProcessingState.Failed to "Delivery result: $resultCode"
            }
            else -> return
        }

        val (id, phone) = intent.dataString?.split("|", limit = 2) ?: return

        updateState(id, phone, state, error)
    }

    suspend fun truncateLog() {
        val lifetime = settings.logLifetimeDays ?: return

        dao.truncateLog(System.currentTimeMillis() - lifetime * 86400000L)
    }

    internal suspend fun sendPendingMessages() {
        while (true) {
            val message = dao.getPending() ?: return
            delay(1L)

            // don't apply limits for expedited messages
        // if (message.message.priority < Message.PRIORITY_EXPEDITED) {
        //     applyLimit() // Old global limit, will be replaced by per-phone limit
        // }

        val phoneConfig: VirtualPhoneConfig? = getPhoneConfigForMessage(message.message)

        if (phoneConfig == null) {
            logsService.insert(
                LogEntry.Priority.ERROR,
                MODULE_NAME,
                "No suitable VirtualPhoneConfig found for message ${message.message.id}. Skipping.",
            )
            updateState(message.message.id, null, ProcessingState.Failed, "No active virtual phone for message")
            continue
        }

        if (phoneConfig.dailySmsLimit != -1 && phoneConfig.currentDailySmsCount >= phoneConfig.dailySmsLimit) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Daily SMS limit reached for phone ${phoneConfig.id} (${phoneConfig.name}). Message ${message.message.id} deferred or failed.",
            )
            // Optionally, keep it pending for next day, or fail it. For now, fail.
            updateState(message.message.id, null, ProcessingState.Failed, "Daily SMS limit reached for ${phoneConfig.name}")
            continue // Try next message
            }


        if (!withContext(NonCancellable) { sendMessage(message, phoneConfig) }) {
                // if message was not sent - don't need any delay before next message
                continue
            }

            // don't apply delay for expedited messages
            if (message.message.priority >= Message.PRIORITY_EXPEDITED) {
                continue
            }

            settings.sendIntervalRange?.let {
                delay(it.random() * 1000L)
            }
        }
    }

    private suspend fun applyLimit() {
        if (!settings.limitEnabled) {
            return
        }

        val processedStats =
            dao.countProcessedFrom(System.currentTimeMillis() - settings.limitPeriod.duration)
        if (processedStats.count < settings.limitValue) {
            return
        }

        delay(settings.limitPeriod.duration - (System.currentTimeMillis() - processedStats.lastTimestamp) + 1000L)
    }

    /**
     * @return `true` if message was sent
     */
    private suspend fun sendMessage(request: MessageWithRecipients, phoneConfig: VirtualPhoneConfig): Boolean {
        if (request.message.validUntil?.before(Date()) == true) {
            updateState(request.message.id, null, ProcessingState.Failed, "TTL expired")
            return false
        }

        if (request.state != ProcessingState.Pending) {
            Log.w(this.javaClass.simpleName, "Unexpected state for message: $request")
            updateState(request.message.id, null, request.state) // Update to its current non-pending state
            return false
        }

        try {
            val originalMessageText = when (request.message.isEncrypted) {
                true -> encryptionService.decrypt(request.message.text)
                false -> request.message.text
            }
            val messageTextWithPrefix = phoneConfig.smsPrefix?.let { it + originalMessageText } ?: originalMessageText

            if (phoneConfig.smppServerId != null) {
                // SMPP Phone
                // For SMPP, we send one by one to handle recipient-specific states if smppService supports it,
                // or we send the same message to all recipients if it doesn't.
                // Assuming smppService.sendSms is per recipient for now for DLR correlation.
                // However, the current smppService.sendSms takes a single destination.
                // We'll iterate here, which is not ideal for multi-recipient messages via SMPP if they are meant to be single PDU.
                // This part might need refinement based on how SMPP typically handles multi-recipient messages.
                // For now, treat each recipient as a separate send via SMPP.

                var allSuccessful = true
                request.recipients.filter { it.state == ProcessingState.Pending }.forEach { recipient ->
                    try {
                        val smppMessageId = smppService.sendSms(
                            phoneId = phoneConfig.id,
                            sourceAddress = phoneConfig.name, // Or a specific source address from phoneConfig
                            destinationAddress = recipient.phoneNumber, // Assuming it's decrypted if needed
                            text = messageTextWithPrefix,
                            messageId = request.message.id // Pass our internal message ID for DLR correlation
                        )
                        logsService.insert(LogEntry.Priority.INFO, MODULE_NAME, "Message ${request.message.id} sent to ${recipient.phoneNumber} via SMPP ${phoneConfig.id}. SMPP ID: $smppMessageId")
                        virtualPhoneConfigDao.incrementSmsCount(phoneConfig.id)
                        // Assuming Sent means successfully handed off to SMSC. Actual delivery is async via DLR.
                        updateState(request.message.id, recipient.phoneNumber, ProcessingState.Sent, smppMessageId)
                    } catch (e: Exception) {
                        allSuccessful = false
                        logsService.insert(LogEntry.Priority.ERROR, MODULE_NAME, "Failed to send SMS to ${recipient.phoneNumber} via SMPP ${phoneConfig.id}: ${e.message}")
                        updateState(request.message.id, recipient.phoneNumber, ProcessingState.Failed, "SMPP send failed: ${e.message}")
                    }
                }
                return allSuccessful // true if all parts were accepted by SMPP server
            } else {
                // Local SIM Phone
                sendSMSLocal(request, phoneConfig, messageTextWithPrefix) // Pass phoneConfig for SIM selection
                // sendSMSLocal internally calls updateState which should lead to incrementSmsCount
                // For local sends, sendSMSLocal handles all recipients as part of SMSManager logic
                return true // Assuming sendSMSLocal handles its own errors and state updates
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateState(
                request.message.id,
                null, // Apply to all recipients if error is general
                ProcessingState.Failed,
                "Can't send message (general error): " + e.message
            )
        }

        return false
    }


    private suspend fun getPhoneConfigForMessage(message: Message): VirtualPhoneConfig? {
        return if (message.virtualPhoneId != null) {
            logsService.insert(LogEntry.Priority.INFO, MODULE_NAME, "Message ${message.id} specified virtualPhoneId: ${message.virtualPhoneId}")
            virtualPhoneConfigDao.getById(message.virtualPhoneId)
        } else {
            logsService.insert(LogEntry.Priority.INFO, MODULE_NAME, "Message ${message.id} did not specify virtualPhoneId. Applying default routing strategy.")
            // Default strategy: find first enabled phone (could be local SIM or SMPP)
            // This might need to be more sophisticated, e.g., prefer local SIMs, or based on message properties
            val enabledPhones = virtualPhoneConfigDao.getEnabledPhones().firstOrNull() // Assuming Flow.firstOrNull()
            val selectedPhone = enabledPhones?.firstOrNull()
            if (selectedPhone != null) {
                logsService.insert(LogEntry.Priority.INFO, MODULE_NAME, "Default routing selected virtualPhoneId: ${selectedPhone.id} for message ${message.id}")
            } else {
                logsService.insert(LogEntry.Priority.WARN, MODULE_NAME, "Default routing failed: No enabled virtual phones found for message ${message.id}.")
            }
            selectedPhone
        }
    }


    suspend fun enqueueReceivedSmppMessage(
        source: EntitySource,
        smppReceivedMessage: me.capcom.smsgateway.modules.messages.data.Message, // This is the DTO
        virtualPhoneConfigId: String
    ) {
        val existingMessage = dao.get(smppReceivedMessage.id) // Assuming smppReceivedMessage.id is unique external ID
        if (existingMessage != null) {
            Log.d(this.javaClass.name, "Message (incoming SMPP) already exists: ${smppReceivedMessage.id}")
            return
        }

        // Adapt DTO to Entity
        // Incoming messages via SMPP usually have one sender (sourceAddress) and one recipient (our virtual number)
        // The `phoneNumbers` in the DTO here would typically be the sender's number.
        // The `text` is the message content.
        // `isEncrypted` should probably be false for incoming standard SMS.

        val messageEntity = Message(
            id = smppReceivedMessage.id.ifEmpty { UUID.randomUUID().toString() }, // Ensure ID
            text = smppReceivedMessage.text,
            withDeliveryReport = false, // Not applicable for MO messages
            simNumber = null, // Not directly applicable, use virtualPhoneConfigId
            validUntil = null, // Not applicable for MO messages
            isEncrypted = smppReceivedMessage.isEncrypted, // Typically false for MO
            skipPhoneValidation = true, // Sender's number, no validation needed
            priority = Message.PRIORITY_DEFAULT,
            source = source, // e.g., EntitySource.SMPP
            state = ProcessingState.Received, // New state or use Pending/Processed
            createdAt = smppReceivedMessage.createdAt.time,
            virtualPhoneId = virtualPhoneConfigId
        )

        // Assuming the first phoneNumber in the DTO is the sender for an MO message
        val senderPhoneNumber = smppReceivedMessage.phoneNumbers.firstOrNull()
            ?: run {
                logsService.insert(LogEntry.Priority.WARN, MODULE_NAME, "Incoming SMPP message ${smppReceivedMessage.id} has no sender phone number.")
                return
            }

        val recipientEntity = MessageRecipient(
            messageId = messageEntity.id,
            phoneNumber = senderPhoneNumber, // Store sender as the "recipient" from perspective of this message entry
            state = ProcessingState.Received, // Or the message's overall state
            error = null, // No error
            processedAt = System.currentTimeMillis() // Mark as processed internally
        )

        val messageWithRecipients = MessageWithRecipients(messageEntity, listOf(recipientEntity))
        dao.insert(messageWithRecipients)

        logsService.insert(LogEntry.Priority.INFO, MODULE_NAME, "Incoming SMPP message from ${senderPhoneNumber} via ${virtualPhoneConfigId} enqueued with ID ${messageEntity.id}")

        events.emit(
            MessageStateChangedEvent(
                messageId = messageEntity.id,
                source = messageEntity.source,
                phoneNumbers = setOf(senderPhoneNumber),
                state = ProcessingState.Received, // Or whatever state is appropriate
                simNumber = null, // Or an indicator for the virtual phone
                error = null,
                virtualPhoneId = virtualPhoneConfigId
            )
        )
    }


    private suspend fun updateState(
        id: String,
        phone: String?,
        state: ProcessingState,
        error: String? = null
    ) {
        if (phone == null) {
            dao.updateRecipientsState(id, state, error)
        } else {
            dao.updateRecipientState(id, phone, state, error)
        }

        val msg = requireNotNull(getMessage(id)) // Fetch the updated message state

        // If the state update was for a specific recipient and successful (Processed or Sent),
        // and it's a local SIM send, increment its counter.
        // For SMPP, counter is incremented immediately after smppService.sendSms call.
        if (phone != null && (state == ProcessingState.Processed || state == ProcessingState.Sent)) {
            msg.message.virtualPhoneId?.let { vpId ->
                virtualPhoneConfigDao.getById(vpId)?.let { config ->
                    if (config.smppServerId == null) { // Only for local SIM sends here, SMPP is handled inline
                        virtualPhoneConfigDao.incrementSmsCount(vpId)
                        logsService.insert(LogEntry.Priority.DEBUG, MODULE_NAME, "Incremented SMS count for local SIM phone $vpId after state update.")
                    }
                }
            }
        }

        events.emit(
            MessageStateChangedEvent(
                id,
                msg.message.source,
                phone?.let { setOf(it) } ?: msg.recipients.map { it.phoneNumber }.toSet(),
                state,
                msg.message.simNumber, // This might need to map to virtualPhone.name or similar
                error,
                msg.message.virtualPhoneId // Added
            )
        )
    }

    private fun selectSimSlotForLocalSend(message: Message, phoneConfig: VirtualPhoneConfig?): Int? {
        // If VirtualPhoneConfig explicitly defines a SIM slot, use it.
        if (phoneConfig?.simSlot != null) {
            // Assuming simSlot in VirtualPhoneConfig is 1-based, convert to 0-based for SmsManager
            return phoneConfig.simSlot - 1
        }

        // If message itself has a simNumber defined (legacy or specific request), use it.
        // This might override the phoneConfig's SIM or be used if phoneConfig doesn't specify one.
        if (message.simNumber != null) {
            return message.simNumber - 1
        }
        
        // Fallback to existing selection logic if no specific SIM is determined by VirtualPhoneConfig or message
        val simSlots = SubscriptionsHelper.selectAvailableSimSlots(context)?.sorted() ?: return null
        if (simSlots.isEmpty()) {
            throw RuntimeException("No SIMs found for local send")
        }

        return when (settings.simSelectionMode) {
            MessagesSettings.SimSelectionMode.OSDefault -> null // Use system default
            MessagesSettings.SimSelectionMode.RoundRobin -> {
                // This rowId is from MessageWithRecipients, which might not be directly available here.
                // This logic might need access to the rowId or a different round-robin mechanism.
                // For now, as a placeholder, let's pick the first available if RoundRobin is selected but rowId isn't easily usable here.
                // val rowId = message. // how to get rowId here?
                // simSlots[(rowId % simSlots.size).toInt()]
                simSlots.first() // Placeholder for RoundRobin without rowId
            }
            MessagesSettings.SimSelectionMode.Random -> simSlots.random()
        }
    }

    private suspend fun sendSMSLocal(request: MessageWithRecipients, phoneConfig: VirtualPhoneConfig, messageText: String) {
        val message = request.message
        val id = message.id

        // Determine SIM slot: use phoneConfig.simSlot if available, else use message.simNumber or default logic
        val simSlotIndex = selectSimSlotForLocalSend(message, phoneConfig)
        val smsManager: SmsManager = getSmsManager(simSlotIndex)

        // If the message didn't have a specific SIM and one was selected, update the message entity
        if (message.simNumber == null && simSlotIndex != null) {
            dao.updateSimNumber(id, simSlotIndex + 1) // Store as 1-based
        }
        // Also update virtualPhoneId if it wasn't set (e.g. default phone selection)
        if (message.virtualPhoneId == null) {
            dao.updateVirtualPhoneId(id, phoneConfig.id)
        }


        request.recipients
            .filter { it.state == ProcessingState.Pending }
            .forEach { rcp ->
                val sourcePhoneNumber = rcp.phoneNumber // This is the destination for the outgoing SMS
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(
                        EventsReceiver.ACTION_SENT,
                        Uri.parse("$id|$sourcePhoneNumber"), // id|destinationPhoneNumber
                        context,
                        EventsReceiver::class.java
                    ).putExtra("virtualPhoneId", phoneConfig.id), // Pass virtualPhoneId for potential DLR handling
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val deliveredIntent = when (message.withDeliveryReport) {
                    false -> null
                    true -> PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(
                            EventsReceiver.ACTION_DELIVERED,
                            Uri.parse("$id|$sourcePhoneNumber"), // id|destinationPhoneNumber
                            context,
                            EventsReceiver::class.java
                        ).putExtra("virtualPhoneId", phoneConfig.id),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }

                try {
                    // Note: messageText already has prefix applied and is decrypted if needed
                    val parts = smsManager.divideMessage(messageText)
                    val phoneNumberToSendTo = when (message.isEncrypted) { // Should not happen if messageText is already decrypted
                        true -> encryptionService.decrypt(sourcePhoneNumber)
                        false -> sourcePhoneNumber
                    }
                    val normalizedPhoneNumber = when (message.skipPhoneValidation) {
                        true -> phoneNumberToSendTo.filter { it.isDigit() || it == '+' }
                        false -> PhoneHelper.filterPhoneNumber(phoneNumberToSendTo, countryCode ?: "RU")
                    }

                    if (parts.size > 1) {
                        smsManager.sendMultipartTextMessage(
                            normalizedPhoneNumber,
                            null, // scAddress
                            parts,
                            ArrayList(parts.map { sentIntent }), // One intent per part
                            deliveredIntent?.let { ArrayList(parts.map { deliveredIntent }) } // One intent per part
                        )
                    } else {
                        smsManager.sendTextMessage(
                            normalizedPhoneNumber,
                            null, // scAddress
                            messageText,
                            sentIntent,
                            deliveredIntent
                        )
                    }
                    // updateState will handle incrementing count for local SIM
                    updateState(id, sourcePhoneNumber, ProcessingState.Processed, "Sent via local SIM ${simSlotIndex?.plus(1) ?: "default"}")
                } catch (th: Throwable) {
                    logsService.insert(
                        LogEntry.Priority.ERROR,
                        MODULE_NAME,
                        "Can't send message via local SIM: " + th.message,
                        mapOf(
                            "stacktrace" to th.stackTraceToString(),
                            "virtualPhoneId" to phoneConfig.id,
                            "simSlotAttempted" to simSlotIndex
                        )
                    )
                    updateState(
                        id,
                        sourcePhoneNumber,
                        ProcessingState.Failed,
                        "sendSMSLocal: " + th.message
                    )
                }
            }
    }

    @SuppressLint("NewApi")
    private fun getSmsManager(simSlotIndex: Int?): SmsManager { // Renamed from simNumber to simSlotIndex (0-based)
        return if (simSlotIndex != null) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw UnsupportedOperationException("SIM selection requires READ_PHONE_STATE permission")
            }

            val subscriptionManager = SubscriptionsHelper.getSubscriptionsManager(context)
                ?: throw UnsupportedOperationException("SIM selection available from API 22")

            subscriptionManager.activeSubscriptionInfoList.find {
                it.simSlotIndex == simSlotIndex // Match with 0-based index
            }?.let {
                if (Build.VERSION.SDK_INT < 31) {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(it.subscriptionId)
                } else {
                    context.getSystemService(SmsManager::class.java)
                        .createForSubscriptionId(it.subscriptionId)
                }
            } ?: throw UnsupportedOperationException("SIM for slot index ${simSlotIndex} not found")
        } else {
            // Use system default SmsManager
            if (Build.VERSION.SDK_INT < 31) {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } else {
                context.getSystemService(SmsManager::class.java)
            }
        }
    }

    private fun resultToErrorMessage(resultCode: Int): String {
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

    companion object {
        private const val MODULE_NAME = "MessagesService"
    }
}