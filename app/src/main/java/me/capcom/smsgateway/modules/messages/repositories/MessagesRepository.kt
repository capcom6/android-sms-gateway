package me.capcom.smsgateway.modules.messages.repositories

import androidx.lifecycle.distinctUntilChanged
import me.capcom.smsgateway.data.dao.MessagesDao

class MessagesRepository(private val dao: MessagesDao) {
    val lastMessages = dao.selectLast().distinctUntilChanged()

    fun getPending() = dao.getPending()
    fun get(id: String) = dao.get(id)
}