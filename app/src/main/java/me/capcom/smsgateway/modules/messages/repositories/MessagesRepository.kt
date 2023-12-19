package me.capcom.smsgateway.modules.messages.repositories

import me.capcom.smsgateway.data.dao.MessageDao

class MessagesRepository(private val dao: MessageDao) {
    val lastMessages = dao.selectLast()

    fun get(id: String) = dao.get(id)
}