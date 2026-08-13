package me.capcom.smsgateway.modules.messages.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessagesTotals
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.messages.MessagesRepository

class MessagesListViewModel(
    private val messagesRepo: MessagesRepository
) : ViewModel() {
    val totals: LiveData<MessagesTotals> =
        messagesRepo.messagesTotals

    private val _queryParams = MutableLiveData<Pair<Int, ProcessingState?>>(chunkSize to null)
    val filter: LiveData<ProcessingState?> = _queryParams.map { it.second }

    private val _messages = MediatorLiveData<List<Message>>()
    val messages: LiveData<List<Message>> = _messages

    init {
        _messages.addSource(_queryParams.switchMap { (limit, state) ->
            messagesRepo.selectLast(limit, state)
        }) {
            _messages.value = it
            hasMore = it.size >= (_queryParams.value?.first ?: chunkSize)
            isLoading = false
        }
        loadMore()
    }

    private var isLoading = false
    private var hasMore = true

    fun loadMore(index: Int = 0) {
        val currentLimit = _queryParams.value?.first ?: 0
        if (currentLimit >= index + chunkSize || isLoading || !hasMore) return

        isLoading = true
        _queryParams.value = (currentLimit + chunkSize) to _queryParams.value?.second
    }

    fun setFilter(state: ProcessingState?) {
        val current = _queryParams.value
        if (current?.second == state) return

        _queryParams.value = chunkSize to state
        hasMore = true
    }

    companion object {
        private const val chunkSize = 50
    }
}