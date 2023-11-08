package me.capcom.smsgateway.ui.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.modules.messages.repositories.MessagesRepository

class MessagesListViewModel(
    private val messagesRepo: MessagesRepository
) : ViewModel() {
    val messages: LiveData<List<Message>> = messagesRepo.messages.asLiveData()
}