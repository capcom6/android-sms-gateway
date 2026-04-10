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
}
