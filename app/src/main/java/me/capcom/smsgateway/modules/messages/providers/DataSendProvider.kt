package me.capcom.smsgateway.modules.messages.providers

import android.content.Context
import android.telephony.SmsManager
import android.util.Base64
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.messages.MessageStateService
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest

open class DataSendProvider(
    context: Context,
    dao: MessagesDao,
    machine: MessageStateService,
    logsService: LogsService,
    simSelector: SimSelector,
    phoneNormalizer: PhoneNormalizer,
    private val encryptionService: EncryptionService,
) : BaseSmsSendProvider(context, dao, machine, logsService, simSelector, phoneNormalizer) {

    override fun supports(content: MessageContent): Boolean = content is MessageContent.Data

    override suspend fun buildPayload(request: StoredSendRequest, smsManager: SmsManager): SmsPayload {
        val message = request.message
        val content = message.content as MessageContent.Data

        val data = when (message.isEncrypted) {
            true -> encryptionService.decrypt(content.data)
            false -> content.data
        }
        val decodedData = try {
            Base64.decode(data, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid Base64 data for message ${message.id}", e)
        }

        return SmsPayload(1) { phoneNumber, sentIntent, deliveredIntent ->
            smsManager.sendDataMessage(
                phoneNumber,
                null,
                content.port.toShort(),
                decodedData,
                sentIntent,
                deliveredIntent
            )
        }
    }
}
