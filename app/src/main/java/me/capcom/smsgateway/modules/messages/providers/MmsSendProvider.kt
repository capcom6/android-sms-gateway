package me.capcom.smsgateway.modules.messages.providers

import android.content.Context
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.mms.MmsSender
import me.capcom.smsgateway.modules.messages.MessageStateService
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest

open class MmsSendProvider(
    private val context: Context,
    private val dao: MessagesDao,
    private val machine: MessageStateService,
    private val simSelector: SimSelector,
    private val phoneNormalizer: PhoneNormalizer,
    private val mmsSender: MmsSender,
    private val encryptionService: EncryptionService,
) : SendMessageProvider {

    override fun supports(content: MessageContent): Boolean = content is MessageContent.Mms

    override suspend fun send(request: StoredSendRequest) {
        val message = request.message
        val id = message.id
        val content = message.content as MessageContent.Mms

        val simNumber = simSelector.select(request.id, request.params)
        if (request.params.simNumber == null && simNumber != null) {
            dao.updateSimNumber(id, simNumber + 1)
        }

        // Resolve a subscription ID + MSISDN from the chosen SIM slot (if any).
        // Verizon and some other US carriers silently drop MMS whose From
        // header is the insert-address-token, so we pass an explicit MSISDN
        // whenever the platform exposes one.
        val subscriptionId = simNumber?.let { SubscriptionsHelper.getSubscriptionId(context, it) }
        val fromMsisdn = simNumber?.let { SubscriptionsHelper.getPhoneNumber(context, it) }
            ?: SubscriptionsHelper.getActiveSimCards(context)?.firstOrNull()?.phoneNumber

        val decryptedContent = if (message.isEncrypted) {
            MessageContent.Mms(
                subject = content.subject?.let { encryptionService.decrypt(it) },
                text = content.text?.let { encryptionService.decrypt(it) },
                attachments = content.attachments.map {
                    it.copy(
                        name = it.name?.let { v -> encryptionService.decrypt(v) },
                        data = encryptionService.decrypt(it.data)
                    )
                }
            )
        } else content

        val recipients = message.phoneNumbers.map { source ->
            phoneNormalizer.normalize(
                source,
                message.isEncrypted,
                request.params.skipPhoneValidation
            )
        }

        dao.updatePartsCount(id, 1 + decryptedContent.attachments.size)

        sendViaMmsSender(id, recipients, decryptedContent, subscriptionId, fromMsisdn)

        // Move all recipients out of Pending immediately so the worker loop
        // does not re-pull this message while we wait for ACTION_MMS_SENT.
        // State transitions to Sent/Failed are driven by processStateIntent.
        // Only transition recipients that are still Pending: if the
        // ACTION_MMS_SENT callback already recorded Sent/Failed, the terminal
        // state must not be overwritten.
        try {
            machine.transitionRecipients(id, ProcessingState.Processed)
        } catch (_: IllegalStateException) {
            // The ACTION_MMS_SENT callback already advanced recipients to
            // Sent/Failed before this call executed. This is a benign race
            // condition; the callback owns the final state.
        }
    }

    /**
     * MmsSender.send is device-bound (FileProvider, PDU composition, real
     * SmsManager) - extracted as the JVM-test seam, mirroring
     * BaseSmsSendProvider.getSmsManager/broadcastIntent.
     */
    protected open fun sendViaMmsSender(
        id: String,
        recipients: List<String>,
        content: MessageContent.Mms,
        subscriptionId: Int?,
        fromMsisdn: String?,
    ) {
        mmsSender.send(id, recipients, content, subscriptionId, fromMsisdn)
    }
}
