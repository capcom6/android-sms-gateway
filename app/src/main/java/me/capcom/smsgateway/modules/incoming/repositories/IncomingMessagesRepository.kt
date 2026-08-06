package me.capcom.smsgateway.modules.incoming.repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.distinctUntilChanged
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageTotals
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.db.IncomingMessagesDao

class IncomingMessagesRepository(private val dao: IncomingMessagesDao) {
    fun selectLast(limit: Int): LiveData<List<IncomingMessage>> =
        dao.selectLast(limit).distinctUntilChanged()

    suspend fun count(type: IncomingMessageType?, from: Long, to: Long): Int =
        dao.count(type, from, to)

    suspend fun select(
        type: IncomingMessageType?,
        from: Long,
        to: Long,
        limit: Int,
        offset: Int
    ): List<IncomingMessage> =
        dao.select(type, from, to, limit, offset)

    fun selectById(id: String): IncomingMessage? = dao.selectById(id)

    val totals: LiveData<IncomingMessageTotals> = dao.getStats().distinctUntilChanged()

    fun insert(message: IncomingMessage) = dao.insert(message)

    fun delete(from: Long, to: Long, types: Set<IncomingMessageType>) = dao.delete(from, to, types)
    suspend fun truncate(until: Long) = dao.truncate(until)

    /** All not-yet-uploaded rows (oldest first) for the A4 cloud upload worker. */
    suspend fun selectForUpload(): List<IncomingMessage> = dao.selectForUpload(null, null, null)

    /**
     * Not-yet-uploaded rows matching optional type/period filters (used by the
     * MessagesExportRequestedEvent path of the A4 worker).
     */
    suspend fun selectForUpload(
        types: Set<IncomingMessageType>?,
        from: Long?,
        until: Long?
    ): List<IncomingMessage> = dao.selectForUpload(types, from, until)

    suspend fun updateUploadedAt(id: String, uploadedAt: Long) = dao.updateUploadedAt(id, uploadedAt)
}
