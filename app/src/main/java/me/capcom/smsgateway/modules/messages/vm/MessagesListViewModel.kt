package me.capcom.smsgateway.modules.messages.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.modules.messages.repositories.MessagesRepository

class MessagesListViewModel(
    messagesRepo: MessagesRepository
) : ViewModel() {
    val messages: LiveData<List<Message>> =
        messagesRepo.lastMessages.distinctUntilChanged().asLiveData(Dispatchers.IO)
}