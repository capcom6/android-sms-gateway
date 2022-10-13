package me.capcom.smsgateway.modules

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.data.dao.MessageDao
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.helpers.PhoneHelper
import me.capcom.smsgateway.receivers.EventsReceiver

class MessagesModule(
    private val context: Context,
    private val dao: MessageDao,
) {
    fun sendMessage(id: String?, text: String, recipients: List<String>, source: Message.Source): MessageWithRecipients {
        val id = id ?: NanoIdUtils.randomNanoId()
        val recipients = recipients.map {
            PhoneHelper.filterPhoneNumber(it) ?: throw IllegalArgumentException("Некорректный номер телефона $it")
        }

        val message = MessageWithRecipients(
            Message(id, text, source),
            recipients.map { MessageRecipient(id, it) },
        )

        dao.insert(message)

        try {
            sendSMS(id, text, recipients)
        } catch (e: Exception) {
            dao.updateState(id, Message.State.Failed)
            return MessageWithRecipients(
                Message(id, text, source, Message.State.Failed),
                recipients.map { MessageRecipient(id, it, Message.State.Failed) },
            )
        }

        return message
    }

    fun getState(id: String): MessageWithRecipients? {
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
            dao.updateState(message.message.id, state)
        }

        return dao.get(id)
    }

    fun processStateIntent(intent: Intent, resultCode: Int) {
        val state = when (intent.action) {
            EventsReceiver.ACTION_SENT -> when (resultCode) {
                Activity.RESULT_OK -> Message.State.Sent
                else -> Message.State.Failed
            }
            EventsReceiver.ACTION_DELIVERED -> Message.State.Delivered
            else -> return
        }
        val (id, phone) = intent.dataString?.split("|", limit = 2) ?: return

        dao.updateState(id, phone, state)
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