package me.capcom.smsgateway.modules.messages.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import me.capcom.smsgateway.modules.messages.repositories.MessagesRepository

class MessageDetailsViewModel(
    private val messagesRepo: MessagesRepository
) : ViewModel() {
    fun get(id: String) = liveData { emit(messagesRepo.get(id)) }
}