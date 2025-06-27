package me.capcom.smsgateway.modules.messages.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest
import me.capcom.smsgateway.modules.messages.repositories.MessagesRepository

class MessageDetailsViewModel(
    private val messagesRepo: MessagesRepository
) : ViewModel() {
    private val _message = MutableLiveData<StoredSendRequest>()
    val message: LiveData<StoredSendRequest> = _message

    fun get(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _message.postValue(messagesRepo.get(id))
        }
    }
}