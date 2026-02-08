package me.stappmus.messagegateway.modules.messages.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import me.stappmus.messagegateway.data.entities.Message
import me.stappmus.messagegateway.data.entities.MessagesTotals
import me.stappmus.messagegateway.modules.messages.MessagesRepository

class MessagesListViewModel(
    private val messagesRepo: MessagesRepository
) : ViewModel() {
    val totals: LiveData<MessagesTotals> =
        messagesRepo.messagesTotals

    private val _limit = MutableLiveData<Int>(chunkSize)
    private val _messages = MediatorLiveData<List<Message>>()
    val messages: LiveData<List<Message>> = _messages

    init {
        _messages.addSource(_limit.switchMap { messagesRepo.selectLast(it) }) {
            _messages.value = it
            hasMore = it.size >= (_limit.value ?: chunkSize)
            isLoading = false
        }
        loadMore()
    }

    private var isLoading = false
    private var hasMore = true

    fun loadMore(index: Int = 0) {
        val currentLimit = _limit.value ?: 0
        if (currentLimit >= index + chunkSize || isLoading || !hasMore) return

        isLoading = true
        _limit.value = currentLimit + chunkSize
    }

    companion object {
        private const val chunkSize = 50
    }
}