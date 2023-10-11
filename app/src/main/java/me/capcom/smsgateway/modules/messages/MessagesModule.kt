package me.capcom.smsgateway.modules.messages

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.data.dao.MessageDao
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.helpers.PhoneHelper
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.receivers.EventsReceiver

class MessagesModule(
    private val context: Context,
    private val dao: MessageDao,
) {
    val events = EventBus()
    private val countryCode: String? = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkCountryIso

    suspend fun sendMessage(id: String?, text: String, recipients: List<String>, source: Message.Source): MessageWithRecipients {
        val id = id ?: NanoIdUtils.randomNanoId()

        val message = MessageWithRecipients(
            Message(id, text, source),
            recipients.map {
                val phoneNumber = PhoneHelper.filterPhoneNumber(it, countryCode ?: "RU")
                MessageRecipient(
                    id,
                    phoneNumber ?: it,
                    phoneNumber?.let { Message.State.Pending } ?: Message.State.Failed
                )
            },
        )

        dao.insert(message)

        events.emitEvent(
            MessageStateChangedEvent(
                id,
                message.state,
                message.recipients.associate {
                    it.phoneNumber to it.state
                }
            )
        )

        try {
            sendSMS(id, text, message.recipients.filter { it.state == Message.State.Pending }.map { it.phoneNumber })
        } catch (e: Exception) {
            updateState(id, null, Message.State.Failed)
            return MessageWithRecipients(
                Message(id, text, source, Message.State.Failed),
                recipients.map { MessageRecipient(id, it, Message.State.Failed) },
            )
        }

        return message
    }

    suspend fun getState(id: String): MessageWithRecipients? {
        val message = dao.get(id)
            ?: return null

        val state = when {
            message.recipients.any { it.state == Message.State.Failed } -> Message.State.Failed
            message.recipients.all { it.state == Message.State.Delivered } -> Message.State.Delivered
            message.recipients.all { it.state == Message.State.Sent } -> Message.State.Sent
            else -> Message.State.Pending
        }

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
        val (id, phone) = intent.dataString?.split("|", limit = 2) ?: return

        updateState(id, phone, state)
    }

    private suspend fun updateState(
        id: String,
        phone: String?,
        state: Message.State
    ) {
        phone?.let {
            dao.updateRecipientState(id, it, state)
        }
            ?: kotlin.run {
                dao.updateRecipientsState(id, state)
            }

        val state = getState(id)?.state ?: state

        events.emitEvent(
            MessageStateChangedEvent(
                id,
                state,
                dao.get(id)?.recipients?.associate {
                    it.phoneNumber to it.state
                } ?: return
            )
        )
    }

    private fun sendSMS(id: String, message: String, recipients: List<String>) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

        recipients.forEach {
            val sentIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(EventsReceiver.ACTION_SENT, Uri.parse("$id|$it"), context, EventsReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val deliveredIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(EventsReceiver.ACTION_DELIVERED, Uri.parse("$id|$it"), context, EventsReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

            smsManager.sendTextMessage(it, null, message, sentIntent, deliveredIntent)
        }
    }
}