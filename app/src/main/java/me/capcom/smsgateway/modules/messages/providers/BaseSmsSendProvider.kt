package me.capcom.smsgateway.modules.messages.providers

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.helpers.SmsManagerHelper
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.messages.MODULE_NAME
import me.capcom.smsgateway.modules.messages.MessageStateService
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest
import me.capcom.smsgateway.receivers.EventsReceiver

abstract class BaseSmsSendProvider(
    protected val context: Context,
    protected val dao: MessagesDao,
    protected val machine: MessageStateService,
    protected val logsService: LogsService,
    private val simSelector: SimSelector,
    protected val phoneNormalizer: PhoneNormalizer,
) : SendMessageProvider {

    override suspend fun send(request: StoredSendRequest) {
        val message = request.message
        val id = message.id

        val simNumber = simSelector.select(request.id, request.params)
        val smsManager = getSmsManager(simNumber)

        if (request.params.simNumber == null && simNumber != null) {
            dao.updateSimNumber(id, simNumber + 1)
        }

        val payload = buildPayload(request, smsManager)
        dao.updatePartsCount(id, payload.partsCount)

        message.phoneNumbers.forEach { sourcePhoneNumber ->
            val requestCode = id.hashCode()
            val sentIntent = broadcastIntent(
                EventsReceiver.ACTION_SENT,
                id,
                sourcePhoneNumber,
                requestCode,
            )
            val deliveredIntent = when (request.params.withDeliveryReport) {
                false -> null
                true -> broadcastIntent(
                    EventsReceiver.ACTION_DELIVERED,
                    id,
                    sourcePhoneNumber,
                    requestCode,
                )
            }

            try {
                val normalizedPhoneNumber = phoneNormalizer.normalize(
                    sourcePhoneNumber,
                    message.isEncrypted,
                    request.params.skipPhoneValidation
                )

                payload.sendFn(normalizedPhoneNumber, sentIntent, deliveredIntent)

                machine.transitionRecipient(id, sourcePhoneNumber, ProcessingState.Processed)
            } catch (th: Throwable) {
                logsService.insert(
                    LogEntry.Priority.ERROR,
                    MODULE_NAME,
                    "Can't send message: " + th.message,
                    mapOf(
                        "stacktrace" to th.stackTraceToString()
                    )
                )

                machine.transitionRecipient(
                    id,
                    sourcePhoneNumber,
                    ProcessingState.Failed,
                    "send: " + th.message
                )
            }
        }
    }

    @SuppressLint("NewApi")
    protected open fun getSmsManager(simNumber: Int?): SmsManager {
        if (simNumber == null) {
            return SmsManagerHelper.forSubscription(context, null)
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw UnsupportedOperationException("SIM selection requires READ_PHONE_STATE permission")
        }

        val subscriptionManager = SubscriptionsHelper.getSubscriptionsManager(context)
            ?: throw UnsupportedOperationException("SIM selection available from API 22")

        val info = subscriptionManager.activeSubscriptionInfoList.find {
            it.simSlotIndex == simNumber
        } ?: throw UnsupportedOperationException("SIM ${simNumber + 1} not found")

        return SmsManagerHelper.forSubscription(context, info.subscriptionId)
    }

    protected open fun broadcastIntent(
        action: String,
        id: String,
        phoneNumber: String,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(
            action,
            Uri.parse("$id|$phoneNumber"),
            context,
            EventsReceiver::class.java
        ),
        PendingIntent.FLAG_MUTABLE
    )

    protected abstract suspend fun buildPayload(request: StoredSendRequest, smsManager: SmsManager): SmsPayload

    protected data class SmsPayload(
        val partsCount: Int,
        val sendFn: (String, PendingIntent, PendingIntent?) -> Unit,
    )
}
