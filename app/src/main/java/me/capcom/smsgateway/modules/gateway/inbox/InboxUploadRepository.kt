package me.capcom.smsgateway.modules.gateway.inbox

class InboxUploadRepository(
    private val dao: InboxUploadDao,
) {
    suspend fun enqueue(
        messageId: String,
        type: String,
        sender: String,
        recipient: String?,
        simNumber: Int?,
        messageCreatedAt: Long,
        contentEncrypted: String,
        attachmentsEncrypted: String?,
    ) {
        dao.insert(
            InboxUploadEntity(
                id = 0,
                messageId = messageId,
                type = type,
                sender = sender,
                recipient = recipient,
                simNumber = simNumber,
                messageCreatedAt = messageCreatedAt,
                contentEncrypted = contentEncrypted,
                attachmentsEncrypted = attachmentsEncrypted,
            )
        )
    }

    suspend fun getPending(limit: Int = 100): List<InboxUploadEntity> =
        dao.getPending(limit = limit)

    suspend fun complete(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.delete(ids)
    }
}
