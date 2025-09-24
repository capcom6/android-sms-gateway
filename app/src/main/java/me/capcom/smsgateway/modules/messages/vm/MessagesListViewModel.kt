package me.capcom.smsgateway.modules.messages.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessagesTotals
import me.capcom.smsgateway.modules.messages.MessagesRepository

class MessagesListViewModel(
    messagesRepo: MessagesRepository
) : ViewModel() {
    val messages: LiveData<List<Message>> =
        messagesRepo.lastMessages

    val totals: LiveData<MessagesTotals> =
        messagesRepo.messagesTotals
}