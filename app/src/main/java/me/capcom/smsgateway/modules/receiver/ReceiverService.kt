package me.capcom.smsgateway.modules.receiver

import android.content.Context
import android.os.Build
import android.provider.Telephony
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import me.capcom.smsgateway.modules.receiver.events.MessageReceivedEvent
import me.capcom.smsgateway.modules.webhooks.WebHooksService
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

class ReceiverService : KoinComponent {
    private val webHooksService: WebHooksService by inject()

    fun export(context: Context, period: Pair<Date, Date>) {
        select(context, period)
            .forEach {
                process(context, it)
            }
    }

    fun process(context: Context, message: InboxMessage) {
        val event = MessageReceivedEvent(
            message = message.body,
            phoneNumber = message.address,
            simNumber = message.subscriptionId?.let {
                SubscriptionsHelper.getSimSlotIndex(
                    context,
                    it
                )
            }?.let { it + 1 },
            receivedAt = message.date,
        )

        webHooksService.emit(WebHookEvent.SmsReceived, event)
    }

    fun select(context: Context, period: Pair<Date, Date>): List<InboxMessage> {
        val projection = mutableListOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            projection += Telephony.Sms.SUBSCRIPTION_ID
        }

        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"
        val selectionArgs = arrayOf(
            period.first.time.toString(),
            period.second.time.toString()
        )
        val sortOrder = Telephony.Sms.DATE

        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection.toTypedArray(),
            selection,
            selectionArgs,
            sortOrder
        )

        val messages = mutableListOf<InboxMessage>()

        cursor?.use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(
                    InboxMessage(
                        id = cursor.getLong(0),
                        address = cursor.getString(1),
                        date = Date(cursor.getLong(2)),
                        body = cursor.getString(3),
                        subscriptionId = when {
                            projection.size > 4 -> cursor.getInt(4).takeIf { it >= 0 }
                            else -> null
                        }
                    )
                )
            }
        }

        return messages
    }
}