package me.stappmus.messagegateway.modules.messages

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import com.android.mms.MmsConfig
import com.klinker.android.send_message.Settings as MmsTransactionSettings
import com.klinker.android.send_message.Transaction
import com.google.android.mms.MMSPart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.stappmus.messagegateway.data.dao.MessagesDao
import me.stappmus.messagegateway.data.entities.Message
import me.stappmus.messagegateway.data.entities.MessageWithRecipients
import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.domain.MessageContent
import me.stappmus.messagegateway.domain.ProcessingState
import me.stappmus.messagegateway.helpers.PhoneHelper
import me.stappmus.messagegateway.helpers.SubscriptionsHelper
import me.stappmus.messagegateway.modules.encryption.EncryptionService
import me.stappmus.messagegateway.modules.events.EventBus
import me.stappmus.messagegateway.modules.health.domain.CheckResult
import me.stappmus.messagegateway.modules.health.domain.Status
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.media.MediaService
import me.stappmus.messagegateway.modules.messages.data.SendParams
import me.stappmus.messagegateway.modules.messages.data.SendRequest
import me.stappmus.messagegateway.modules.messages.data.StoredSendRequest
import me.stappmus.messagegateway.modules.messages.events.MessageStateChangedEvent
import me.stappmus.messagegateway.modules.messages.workers.LogTruncateWorker
import me.stappmus.messagegateway.modules.messages.workers.SendMessagesWorker
import me.stappmus.messagegateway.receivers.EventsReceiver
import java.io.File
import java.util.Date

class MessagesService(
    private val context: Context,
    private val settings: MessagesSettings,
    private val dao: MessagesDao,    // todo: use MessagesRepository
    private val messages: MessagesRepository,
    private val encryptionService: EncryptionService,
    private val events: EventBus,
    private val logsService: LogsService,
    private val mediaService: MediaService,
) {
    val processingOrder
        get() = settings.processingOrder

    private val countryCode: String? =
        (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkCountryIso

    //#region Health
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
    //#endregion

    //#region Lifecycle
    fun start(context: Context) {
        SendMessagesWorker.start(context, false)
        LogTruncateWorker.start(context)
    }

    fun stop(context: Context) {
        LogTruncateWorker.stop(context)
        SendMessagesWorker.stop(context)
    }
    //#endregion

    //#region Send
    fun enqueueMessage(request: SendRequest) {
        if (getMessage(request.message.id) != null) {
            Log.d(this.javaClass.name, "Message already exists: ${request.message.id}")
            return
        }

        messages.enqueue(request)

        val priority = request.params.priority ?: Message.PRIORITY_DEFAULT

        SendMessagesWorker.start(context, priority >= Message.PRIORITY_EXPEDITED)
    }
    //#endregion

    //#region Read
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

    /**
     * Count messages based on state and date range
     */
    fun countMessages(source: EntitySource, state: ProcessingState?, start: Long, end: Long) =
        dao.count(source, state, start, end)

    /**
     * Get messages with pagination and filtering
     */
    fun selectMessages(
        source: EntitySource,
        state: ProcessingState?,
        start: Long,
        end: Long,
        limit: Int,
        offset: Int
    ) = dao.select(source, state, start, end, limit, offset)
    //#endregion

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

                intent.hasExtra("uri") || intent.hasExtra("content_uri") -> ProcessingState.Sent to null
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

        if (intent.action == EventsReceiver.ACTION_SENT) {
            cleanupMmsPayloadFile(intent)
        }

        val (id, phone) = intent.dataString?.split("|", limit = 2) ?: return

        updateState(id, phone, state, error)
    }

    suspend fun truncateLog() {
        val lifetime = settings.logLifetimeDays ?: return

        dao.truncateLog(System.currentTimeMillis() - lifetime * 86400000L)
    }

    internal suspend fun sendPendingMessages() {
        var previousPriority = Message.PRIORITY_MIN

        while (true) {
            val message = messages.getPending(settings.processingOrder) ?: return
            delay(1L)

            val priority = message.params.priority ?: Message.PRIORITY_DEFAULT

            // apply limits if:
            // - message is not expedited
            // - message is expedited and previous message had higher or equal priority
            if (priority < Message.PRIORITY_EXPEDITED
                || previousPriority >= priority
            ) {
                applyLimit()
            }

            if (!withContext(NonCancellable) { sendMessage(message) }) {
                // if message was not sent - don't need any delay before next message
                continue
            }

            // don't apply delay for expedited messages
            if (priority >= Message.PRIORITY_EXPEDITED && previousPriority < priority) {
                previousPriority = priority
                continue
            }

            previousPriority = priority

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
    private suspend fun sendMessage(request: StoredSendRequest): Boolean {
        if (request.params.validUntil?.before(Date()) == true) {
            updateState(request.message.id, null, ProcessingState.Failed, "TTL expired")
            return false
        }

        try {
            sendSMS(request)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            updateState(
                request.message.id,
                null,
                ProcessingState.Failed,
                "Can't send message: " + e.message
            )
        }

        return false
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

        val msg = requireNotNull(getMessage(id))

        events.emit(
            MessageStateChangedEvent(
                id,
                msg.message.source,
                phone?.let { setOf(it) } ?: msg.recipients.map { it.phoneNumber }.toSet(),
                state,
                msg.message.simNumber,
                msg.message.partsCount,
                error
            )
        )
    }

    private fun selectSimNumber(id: Long, params: SendParams): Int? {
        if (params.simNumber != null) {
            return params.simNumber - 1
        }

        val simSlots = SubscriptionsHelper.selectAvailableSimSlots(context)?.sorted() ?: return null
        if (simSlots.isEmpty()) {
            throw RuntimeException("No SIMs found")
        }

        return when (settings.simSelectionMode) {
            MessagesSettings.SimSelectionMode.OSDefault -> null
            MessagesSettings.SimSelectionMode.RoundRobin -> simSlots[(id % simSlots.size).toInt()]
            MessagesSettings.SimSelectionMode.Random -> simSlots.random()
        }
    }

    private suspend fun sendSMS(request: StoredSendRequest) {
        val message = request.message
        val id = message.id

        val simNumber = selectSimNumber(request.id, request.params)
        val smsManager: SmsManager = getSmsManager(simNumber)

        if (request.params.simNumber == null && simNumber != null) {
            dao.updateSimNumber(id, simNumber + 1)
        }

        val sendFn: (String, PendingIntent, PendingIntent?) -> Unit =
            when (val content = message.content) {
                is MessageContent.Text -> {
                    // Handle text messages
                    val text = when (message.isEncrypted) {
                        true -> encryptionService.decrypt(content.text)
                        false -> content.text
                    }

                    val parts = smsManager.divideMessage(text)
                    dao.updatePartsCount(id, parts.size)

                    if (parts.size > 1) {
                        { phoneNumber: String, sentIntent: PendingIntent, deliveredIntent: PendingIntent? ->
                            smsManager.sendMultipartTextMessage(
                                phoneNumber,
                                null,
                                parts,
                                ArrayList(List(parts.size) { sentIntent }),
                                deliveredIntent?.let { intent -> ArrayList(List(parts.size) { intent }) }
                            )
                        }
                    } else {
                        { phoneNumber: String, sentIntent: PendingIntent, deliveredIntent: PendingIntent? ->
                            smsManager.sendTextMessage(
                                phoneNumber,
                                null,
                                text,
                                sentIntent,
                                deliveredIntent
                            )
                        }
                    }
                }

                is MessageContent.Data -> {
                    val data = when (message.isEncrypted) {
                        true -> encryptionService.decrypt(content.data)
                        false -> content.data
                    }
                    val decodedData = try {
                        Base64.decode(data, Base64.DEFAULT)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException(
                            "Invalid Base64 data for message ${message.id}",
                            e
                        )
                    }
                    dao.updatePartsCount(id, 1);

                    { phoneNumber: String, sentIntent: PendingIntent, deliveredIntent: PendingIntent? ->
                        smsManager.sendDataMessage(
                            phoneNumber,
                            null,  // scAddress
                            content.port.toShort(),
                            decodedData,
                            sentIntent,
                            deliveredIntent
                        )
                    }
                }

                is MessageContent.Mms -> {
                    val attachments = content.attachments
                    if (attachments.isEmpty()) {
                        throw IllegalArgumentException("MMS requires at least one attachment")
                    }

                    val text = content.text?.let {
                        when (message.isEncrypted) {
                            true -> encryptionService.decrypt(it)
                            false -> it
                        }
                    }

                    dao.updatePartsCount(id, attachments.size + if (text.isNullOrBlank()) 0 else 1)

                    val sendMultimedia: (String, PendingIntent, PendingIntent?) -> Unit = { phoneNumber: String, _: PendingIntent, _: PendingIntent? ->
                        sendMms(
                            messageId = id,
                            phoneNumber = phoneNumber,
                            text = text,
                            attachments = attachments,
                            simSlotIndex = simNumber,
                        )
                    }

                    sendMultimedia
                }
            }



        request.message.phoneNumbers
            .forEach { sourcePhoneNumber ->
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(
                        EventsReceiver.ACTION_SENT,
                        Uri.parse("$id|$sourcePhoneNumber"),
                        context,
                        EventsReceiver::class.java
                    ),
                    PendingIntent.FLAG_MUTABLE
                )
                val deliveredIntent = when (request.params.withDeliveryReport) {
                    false -> null
                    true -> PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(
                            EventsReceiver.ACTION_DELIVERED,
                            Uri.parse("$id|$sourcePhoneNumber"),
                            context,
                            EventsReceiver::class.java
                        ),
                        PendingIntent.FLAG_MUTABLE
                    )
                }

                try {
                    val phoneNumber = when (message.isEncrypted) {
                        true -> encryptionService.decrypt(sourcePhoneNumber)
                        false -> sourcePhoneNumber
                    }
                    val normalizedPhoneNumber = when (request.params.skipPhoneValidation) {
                        true -> phoneNumber.filter { it.isDigit() || it == '+' }
                        false -> PhoneHelper.filterPhoneNumber(phoneNumber, countryCode ?: "RU")
                    }

                    sendFn(normalizedPhoneNumber, sentIntent, deliveredIntent)

                    updateState(id, sourcePhoneNumber, ProcessingState.Processed)
                } catch (th: Throwable) {
                    logsService.insert(
                        LogEntry.Priority.ERROR,
                        MODULE_NAME,
                        "Can't send message: " + th.message,
                        mapOf(
                            "stacktrace" to th.stackTraceToString(),
                        )
                    )

                    updateState(
                        id,
                        sourcePhoneNumber,
                        ProcessingState.Failed,
                        "sendSMS: " + th.message
                    )
                }
            }
    }

    private fun sendMms(
        messageId: String,
        phoneNumber: String,
        text: String?,
        attachments: List<me.stappmus.messagegateway.domain.MmsAttachment>,
        simSlotIndex: Int?,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            throw UnsupportedOperationException("MMS sending requires Android 5.0+")
        }

        val subscriptionId = simSlotIndex?.let { slot ->
            SubscriptionsHelper.getSubscriptionId(context, slot)
        }

        val settings = MmsTransactionSettings().apply {
            setUseSystemSending(true)
            setGroup(false)
            setDeliveryReports(false)
            setSubscriptionId(subscriptionId)
        }
        Transaction(context, settings)

        val parts = buildMmsParts(phoneNumber, text, attachments)
        val messageInfo = Transaction.getBytes(
            context,
            false,
            null,
            arrayOf(phoneNumber),
            parts.toTypedArray(),
            null,
        )

        val payloadFile = createMmsPayloadFile(messageId, phoneNumber, messageInfo.bytes)
        val payloadUri = Uri.Builder()
            .scheme("content")
            .authority(context.packageName + ".MmsFileProvider")
            .path(payloadFile.name)
            .build()

        val callbackIntent = Intent(
            EventsReceiver.ACTION_SENT,
            Uri.parse("$messageId|$phoneNumber"),
            context,
            EventsReceiver::class.java,
        ).apply {
            putExtra("uri", payloadUri.toString())
            putExtra(EXTRA_MMS_FILE_PATH, payloadFile.absolutePath)
        }
        val callbackPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            callbackIntent,
            PendingIntent.FLAG_MUTABLE,
        )

        val configOverrides = Bundle().apply {
            putBoolean(SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED, false)
            val httpParams = MmsConfig.getHttpParams()
            if (!httpParams.isNullOrBlank()) {
                putString(SmsManager.MMS_CONFIG_HTTP_PARAMS, httpParams)
            }
            putInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, MmsConfig.getMaxMessageSize())
        }

        getSmsManager(simSlotIndex).sendMultimediaMessage(
            context,
            payloadUri,
            null,
            configOverrides,
            callbackPendingIntent,
        )
    }

    private fun buildMmsParts(
        phoneNumber: String,
        text: String?,
        attachments: List<me.stappmus.messagegateway.domain.MmsAttachment>,
    ): List<MMSPart> {
        val parts = mutableListOf<MMSPart>()

        attachments.forEachIndexed { index, attachment ->
            val bytes = mediaService.resolveOutgoingAttachmentBytes(context, attachment)
                ?: throw IllegalArgumentException(
                    "MMS attachment bytes unavailable for ${attachment.id}. Submit attachment data with the request"
                )

            val part = MMSPart().apply {
                Name = attachment.filename
                    ?.takeIf { it.isNotBlank() }
                    ?: "attachment_${index + 1}_${phoneNumber.filter { it.isDigit() }}"
                MimeType = attachment.mimeType
                Data = bytes
            }
            parts += part
        }

        text?.takeIf { it.isNotBlank() }?.let {
            parts += MMSPart().apply {
                Name = "text"
                MimeType = "text/plain"
                Data = it.toByteArray(Charsets.UTF_8)
            }
        }

        return parts
    }

    private fun createMmsPayloadFile(
        messageId: String,
        phoneNumber: String,
        payload: ByteArray,
    ): File {
        val sanitizedPhone = phoneNumber.filter { it.isDigit() }
            .ifBlank { "recipient" }
        val file = File(context.cacheDir, "send.$messageId.$sanitizedPhone.dat")
        file.writeBytes(payload)
        return file
    }

    private fun cleanupMmsPayloadFile(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_MMS_FILE_PATH) ?: return

        runCatching {
            File(path).takeIf { it.exists() }?.delete()
        }
    }

    @SuppressLint("NewApi")
    private fun getSmsManager(simNumber: Int?): SmsManager {
        return if (simNumber != null) {
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
                it.simSlotIndex == simNumber
            }?.let {
                if (Build.VERSION.SDK_INT < 31) {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(it.subscriptionId)
                } else {
                    context.getSystemService(SmsManager::class.java)
                        .createForSubscriptionId(it.subscriptionId)
                }
            } ?: throw UnsupportedOperationException("SIM ${simNumber + 1} not found")
        } else {
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
            SmsManager.MMS_ERROR_UNSPECIFIED -> "MMS_ERROR_UNSPECIFIED (Generic MMS transport failure)"
            SmsManager.MMS_ERROR_INVALID_APN -> "MMS_ERROR_INVALID_APN (APN configuration is invalid for MMS)"
            SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS -> "MMS_ERROR_UNABLE_CONNECT_MMS (Unable to connect to MMSC)"
            SmsManager.MMS_ERROR_HTTP_FAILURE -> "MMS_ERROR_HTTP_FAILURE (HTTP error while sending MMS)"
            SmsManager.MMS_ERROR_IO_ERROR -> "MMS_ERROR_IO_ERROR (I/O error while sending MMS)"
            SmsManager.MMS_ERROR_RETRY -> "MMS_ERROR_RETRY (MMS send should be retried)"
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
        private const val EXTRA_MMS_FILE_PATH = "mms_file_path"
    }
}
