package me.capcom.smsgateway.modules.gateway.inbox

import android.content.Context
import android.util.Base64
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.gateway.GatewayApi.InboxMessageType
import me.capcom.smsgateway.modules.gateway.MODULE_NAME
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

class InboxUploadService : KoinComponent {
    private val encryptionService: EncryptionService by inject()
    private val repository: InboxUploadRepository by inject()
    private val logsService: LogsService by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = GsonBuilder().configure().create()

    fun enqueue(
        context: Context,
        message: InboxMessage,
        sender: String,
        recipient: String?,
        simNumber: Int?,
        date: Date,
    ) {
        scope.launch {
            try {
                val mapped = mapAndEncrypt(message) ?: return@launch
                repository.enqueue(
                    messageId = mapped.messageId,
                    type = mapped.type,
                    sender = sender,
                    recipient = recipient,
                    simNumber = simNumber,
                    messageCreatedAt = date.time,
                    contentEncrypted = mapped.contentEncrypted,
                    attachmentsEncrypted = mapped.attachmentsEncrypted,
                )
                InboxUploadWorker.start(context)
            } catch (e: Exception) {
                logsService.insert(
                    LogEntry.Priority.ERROR,
                    MODULE_NAME,
                    "Failed to prepare inbox upload",
                    mapOf(
                        "error" to (e.message ?: e.toString()),
                        "stackTrace" to e.stackTraceToString(),
                    )
                )
            }
        }
    }

    private suspend fun mapAndEncrypt(message: InboxMessage): Mapped? {
        return when (message) {
            is InboxMessage.Text -> Mapped(
                messageId = message.hashCode().toUInt().toString(16),
                type = InboxMessageType.SMS,
                contentEncrypted = encryptionService.encrypt(message.text),
                attachmentsEncrypted = null,
            )

            is InboxMessage.Data -> Mapped(
                messageId = message.hashCode().toUInt().toString(16),
                type = InboxMessageType.DATA_SMS,
                contentEncrypted = encryptionService.encrypt(
                    Base64.encodeToString(message.data ?: ByteArray(0), Base64.NO_WRAP),
                ),
                attachmentsEncrypted = null,
            )

            is InboxMessage.MmsHeaders -> null

            is InboxMessage.MMS -> {
                val attachments = message.attachments.map {
                    EncryptedAttachment(
                        partId = it.partId,
                        contentType = it.contentType,
                        name = encryptionService.encrypt(it.name ?: ""),
                        size = it.size,
                        data = encryptionService.encrypt(it.data ?: ""),
                    )
                }
                Mapped(
                    messageId = message.messageId,
                    type = InboxMessageType.MMS_DOWNLOADED,
                    contentEncrypted = encryptionService.encrypt(message.body ?: ""),
                    attachmentsEncrypted = gson.toJson(attachments),
                )
            }
        }
    }

    private data class Mapped(
        val messageId: String,
        val type: String,
        val contentEncrypted: String,
        val attachmentsEncrypted: String?,
    )
}
