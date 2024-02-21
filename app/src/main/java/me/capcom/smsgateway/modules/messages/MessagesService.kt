package me.capcom.smsgateway.modules.messages

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import me.capcom.smsgateway.data.dao.MessageDao
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.helpers.PhoneHelper
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.messages.data.SendRequest
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import me.capcom.smsgateway.modules.messages.workers.SendMessagesWorker
import me.capcom.smsgateway.receivers.EventsReceiver
import java.util.Date

class MessagesService(
    private val context: Context,
    private val settings: MessagesSettings,
    private val dao: MessageDao,    // todo: use MessagesRepository
    private val encryptionService: EncryptionService,
) {
    val events = EventBus()

    private val countryCode: String? =
        (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkCountryIso

    init {
        SendMessagesWorker.start(context)
    }

    fun enqueueMessage(request: SendRequest) {
        if (getMessage(request.message.id) != null) {
            Log.d(this.javaClass.name, "Message already exists: ${request.message.id}")
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
                request.source,
            ),
            request.message.phoneNumbers.map {
                MessageRecipient(
                    request.message.id,
                    it,
                    Message.State.Pending
                )
            },
        )

        dao.insert(message)

        SendMessagesWorker.start(context)
    }

    fun getMessage(id: String): MessageWithRecipients? {
        val message = dao.get(id)
            ?: return null

        val state = message.state

        if (state == message.message.state) {
            return message
        }

        if (state != message.message.state) {
            dao.updateMessageState(message.message.id, state)
        }

        return dao.get(id)
    }

    suspend fun processStateIntent(intent: Intent, resultCode: Int) {
        val state = when (intent.action) {
            EventsReceiver.ACTION_SENT -> when (resultCode) {
                Activity.RESULT_OK -> Message.State.Sent
                else -> Message.State.Failed
            }

            EventsReceiver.ACTION_DELIVERED -> Message.State.Delivered
            else -> return
        }
        val error = when (resultCode) {
            Activity.RESULT_OK -> null
            else -> "Send result: " + this.resultToErrorMessage(resultCode)
        }

        val (id, phone) = intent.dataString?.split("|", limit = 2) ?: return

        updateState(id, phone, state, error)
    }

    internal suspend fun sendPendingMessages(): Boolean {
        val messages = dao.selectPending()
        if (messages.isEmpty()) {
            return false
        }

        for (message in messages) {
            sendMessage(message)

            if (settings.secondsBetweenMessages > 0) {
                delay((0..settings.secondsBetweenMessages).random() * 1000L)
            }
        }

        return true
    }

    private suspend fun sendMessage(request: MessageWithRecipients) {
        if (request.message.validUntil?.before(Date()) == true) {
            updateState(request.message.id, null, Message.State.Failed, "TTL expired")
            return
        }

        if (request.state != Message.State.Pending) {
            // не ясно когда такая ситуация может возникнуть
            Log.w(this.javaClass.simpleName, "Unexpected state for message: $request")
            updateState(request.message.id, null, request.state)
            return
        }

        try {
            sendSMS(request)
        } catch (e: Exception) {
            e.printStackTrace()
            updateState(request.message.id, null, Message.State.Failed, "Sending: " + e.message)
        }
    }

    private suspend fun updateState(
        id: String,
        phone: String?,
        state: Message.State,
        error: String? = null
    ) {
        phone?.let {
            dao.updateRecipientState(id, it, state, error)
        }
            ?: kotlin.run {
                dao.updateRecipientsState(id, state, error)
            }

        val msg = requireNotNull(getMessage(id))

        events.emitEvent(
            MessageStateChangedEvent(
                id,
                msg.state,
                msg.message.source,
                msg.recipients.map {
                    MessageStateChangedEvent.Recipient(
                        it.phoneNumber,
                        it.state,
                        it.error
                    )
                }
            )
        )
    }

    private suspend fun sendSMS(request: MessageWithRecipients) {
        val message = request.message
        val id = message.id

        val smsManager: SmsManager = getSmsManager(message.simNumber?.let { it - 1 })

        @Suppress("NAME_SHADOWING")
        val messageText = when (message.isEncrypted) {
            true -> encryptionService.decrypt(message.text)
            false -> message.text
        }

        request.recipients
            .filter { it.state == Message.State.Pending }
            .forEach { rcp ->
                val sourcePhoneNumber = rcp.phoneNumber
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(
                        EventsReceiver.ACTION_SENT,
                        Uri.parse("$id|$sourcePhoneNumber"),
                        context,
                        EventsReceiver::class.java
                    ),
                    PendingIntent.FLAG_IMMUTABLE
                )
                val deliveredIntent = when (message.withDeliveryReport) {
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
                        PendingIntent.FLAG_IMMUTABLE
                    )
                }

                try {
                    val parts = smsManager.divideMessage(messageText)
                    val phoneNumber = when (message.isEncrypted) {
                        true -> encryptionService.decrypt(sourcePhoneNumber)
                        false -> sourcePhoneNumber
                    }
                    val normalizedPhoneNumber = when (message.skipPhoneValidation) {
                        true -> phoneNumber.filter { it.isDigit() || it == '+' }
                        false -> PhoneHelper.filterPhoneNumber(phoneNumber, countryCode ?: "RU")
                    }

                    if (parts.size > 1) {
                        smsManager.sendMultipartTextMessage(
                            normalizedPhoneNumber,
                            null,
                            parts,
                            ArrayList(parts.map { sentIntent }),
                            deliveredIntent?.let { ArrayList(parts.map { deliveredIntent }) }
                        )
                    } else {
                        smsManager.sendTextMessage(
                            normalizedPhoneNumber,
                            null,
                            messageText,
                            sentIntent,
                            deliveredIntent
                        )
                    }

                    updateState(id, sourcePhoneNumber, Message.State.Processed)
                } catch (th: Throwable) {
                    th.printStackTrace()
                    updateState(
                        id,
                        sourcePhoneNumber,
                        Message.State.Failed,
                        "Sending: " + th.message
                    )
                }
        }
    }

    private fun getSmsManager(simNumber: Int?): SmsManager {
        return if (simNumber != null) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw UnsupportedOperationException("SIM selection requires READ_PHONE_STATE permission")
            }

            @Suppress("DEPRECATION")
            val subscriptionManager: SubscriptionManager = when {
                Build.VERSION.SDK_INT < 22 -> throw UnsupportedOperationException("SIM selection available from API 22")
                Build.VERSION.SDK_INT < 31 -> SubscriptionManager.from(context)
                else -> context.getSystemService(SubscriptionManager::class.java)
            }

            if (subscriptionManager.activeSubscriptionInfoCount <= simNumber) {
                throw UnsupportedOperationException("SIM ${simNumber + 1} not found")
            }

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
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "RESULT_ERROR_GENERIC_FAILURE"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "RESULT_ERROR_RADIO_OFF"
            SmsManager.RESULT_ERROR_NULL_PDU -> "RESULT_ERROR_NULL_PDU"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "RESULT_ERROR_NO_SERVICE"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "RESULT_ERROR_LIMIT_EXCEEDED"
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "RESULT_ERROR_FDN_CHECK_FAILURE"
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NOT_ALLOWED"
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED"
            SmsManager.RESULT_RADIO_NOT_AVAILABLE -> "RESULT_RADIO_NOT_AVAILABLE"
            SmsManager.RESULT_NETWORK_REJECT -> "RESULT_NETWORK_REJECT"
            SmsManager.RESULT_INVALID_ARGUMENTS -> "RESULT_INVALID_ARGUMENTS"
            SmsManager.RESULT_INVALID_STATE -> "RESULT_INVALID_STATE"
            SmsManager.RESULT_NO_MEMORY -> "RESULT_NO_MEMORY"
            SmsManager.RESULT_INVALID_SMS_FORMAT -> "RESULT_INVALID_SMS_FORMAT"
            SmsManager.RESULT_SYSTEM_ERROR -> "RESULT_SYSTEM_ERROR"
            SmsManager.RESULT_MODEM_ERROR -> "RESULT_MODEM_ERROR"
            SmsManager.RESULT_NETWORK_ERROR -> "RESULT_NETWORK_ERROR"
            SmsManager.RESULT_ENCODING_ERROR -> "RESULT_ENCODING_ERROR"
            SmsManager.RESULT_INVALID_SMSC_ADDRESS -> "RESULT_INVALID_SMSC_ADDRESS"
            SmsManager.RESULT_OPERATION_NOT_ALLOWED -> "RESULT_OPERATION_NOT_ALLOWED"
            SmsManager.RESULT_INTERNAL_ERROR -> "RESULT_INTERNAL_ERROR"
            SmsManager.RESULT_NO_RESOURCES -> "RESULT_NO_RESOURCES"
            SmsManager.RESULT_CANCELLED -> "RESULT_CANCELLED"
            SmsManager.RESULT_REQUEST_NOT_SUPPORTED -> "RESULT_REQUEST_NOT_SUPPORTED"
            SmsManager.RESULT_NO_BLUETOOTH_SERVICE -> "RESULT_NO_BLUETOOTH_SERVICE"
            SmsManager.RESULT_INVALID_BLUETOOTH_ADDRESS -> "RESULT_INVALID_BLUETOOTH_ADDRESS"
            SmsManager.RESULT_BLUETOOTH_DISCONNECTED -> "RESULT_BLUETOOTH_DISCONNECTED"
            SmsManager.RESULT_UNEXPECTED_EVENT_STOP_SENDING -> "RESULT_UNEXPECTED_EVENT_STOP_SENDING"
            SmsManager.RESULT_SMS_BLOCKED_DURING_EMERGENCY -> "RESULT_SMS_BLOCKED_DURING_EMERGENCY"
            SmsManager.RESULT_SMS_SEND_RETRY_FAILED -> "RESULT_SMS_SEND_RETRY_FAILED"
            SmsManager.RESULT_REMOTE_EXCEPTION -> "RESULT_REMOTE_EXCEPTION"
            SmsManager.RESULT_NO_DEFAULT_SMS_APP -> "RESULT_NO_DEFAULT_SMS_APP"
            SmsManager.RESULT_RIL_RADIO_NOT_AVAILABLE -> "RESULT_RIL_RADIO_NOT_AVAILABLE"
            SmsManager.RESULT_RIL_SMS_SEND_FAIL_RETRY -> "RESULT_RIL_SMS_SEND_FAIL_RETRY"
            SmsManager.RESULT_RIL_NETWORK_REJECT -> "RESULT_RIL_NETWORK_REJECT"
            SmsManager.RESULT_RIL_INVALID_STATE -> "RESULT_RIL_INVALID_STATE"
            SmsManager.RESULT_RIL_INVALID_ARGUMENTS -> "RESULT_RIL_INVALID_ARGUMENTS"
            SmsManager.RESULT_RIL_NO_MEMORY -> "RESULT_RIL_NO_MEMORY"
            SmsManager.RESULT_RIL_REQUEST_RATE_LIMITED -> "RESULT_RIL_REQUEST_RATE_LIMITED"
            SmsManager.RESULT_RIL_INVALID_SMS_FORMAT -> "RESULT_RIL_INVALID_SMS_FORMAT"
            SmsManager.RESULT_RIL_SYSTEM_ERR -> "RESULT_RIL_SYSTEM_ERR"
            SmsManager.RESULT_RIL_ENCODING_ERR -> "RESULT_RIL_ENCODING_ERR"
            SmsManager.RESULT_RIL_INVALID_SMSC_ADDRESS -> "RESULT_RIL_INVALID_SMSC_ADDRESS"
            SmsManager.RESULT_RIL_MODEM_ERR -> "RESULT_RIL_MODEM_ERR"
            SmsManager.RESULT_RIL_NETWORK_ERR -> "RESULT_RIL_NETWORK_ERR"
            SmsManager.RESULT_RIL_INTERNAL_ERR -> "RESULT_RIL_INTERNAL_ERR"
            SmsManager.RESULT_RIL_REQUEST_NOT_SUPPORTED -> "RESULT_RIL_REQUEST_NOT_SUPPORTED"
            SmsManager.RESULT_RIL_INVALID_MODEM_STATE -> "RESULT_RIL_INVALID_MODEM_STATE"
            SmsManager.RESULT_RIL_NETWORK_NOT_READY -> "RESULT_RIL_NETWORK_NOT_READY"
            SmsManager.RESULT_RIL_OPERATION_NOT_ALLOWED -> "RESULT_RIL_OPERATION_NOT_ALLOWED"
            SmsManager.RESULT_RIL_NO_RESOURCES -> "RESULT_RIL_NO_RESOURCES"
            SmsManager.RESULT_RIL_CANCELLED -> "RESULT_RIL_CANCELLED"
            SmsManager.RESULT_RIL_SIM_ABSENT -> "RESULT_RIL_SIM_ABSENT"
            SmsManager.RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED -> "RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED"
            SmsManager.RESULT_RIL_ACCESS_BARRED -> "RESULT_RIL_ACCESS_BARRED"
            SmsManager.RESULT_RIL_BLOCKED_DUE_TO_CALL -> "RESULT_RIL_BLOCKED_DUE_TO_CALL"
            else -> "UNKNOWN"
        }
    }
}