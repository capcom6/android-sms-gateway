package me.capcom.smsgateway.modules.messages.providers

import android.content.Context
import android.telephony.SmsManager
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.messages.MessageStateService
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest

open class TextSendProvider(
    context: Context,
    dao: MessagesDao,
    machine: MessageStateService,
    logsService: LogsService,
    simSelector: SimSelector,
    phoneNormalizer: PhoneNormalizer,
    private val encryptionService: EncryptionService,
) : BaseSmsSendProvider(context, dao, machine, logsService, simSelector, phoneNormalizer) {

    override fun supports(content: MessageContent): Boolean = content is MessageContent.Text

    override suspend fun buildPayload(request: StoredSendRequest, smsManager: SmsManager): SmsPayload {
        val message = request.message
        val content = message.content as MessageContent.Text

        val text = when (message.isEncrypted) {
            true -> encryptionService.decrypt(content.text)
            false -> content.text
        }

        val parts = smsManager.divideMessage(text)

        return if (parts.size > 1) {
            SmsPayload(parts.size) { phoneNumber, sentIntent, deliveredIntent ->
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    ArrayList(List(parts.size) { sentIntent }),
                    deliveredIntent?.let { intent -> ArrayList(List(parts.size) { intent }) }
                )
            }
        } else {
            SmsPayload(parts.size) { phoneNumber, sentIntent, deliveredIntent ->
                smsManager.sendTextMessage(phoneNumber, null, text, sentIntent, deliveredIntent)
            }
        }
    }
}
