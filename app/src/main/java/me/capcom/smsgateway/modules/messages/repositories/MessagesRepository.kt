package me.capcom.smsgateway.modules.messages.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.data.dao.MessageDao

class MessagesRepository(private val dao: MessageDao) {
    val lastMessages = dao.selectLast()

    suspend fun get(id: String) = withContext(Dispatchers.IO) { dao.get(id) }
}