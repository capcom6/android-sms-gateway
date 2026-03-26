package me.capcom.smsgateway.modules.incoming.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageTotals
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository

class IncomingMessagesListViewModel(
    private val repository: IncomingMessagesRepository,
) : ViewModel() {
    val totals: LiveData<IncomingMessageTotals> = repository.totals

    private val limit = MutableLiveData(chunkSize)
    private val _messages = MediatorLiveData<List<IncomingMessage>>()
    val messages: LiveData<List<IncomingMessage>> = _messages

    private var isLoading = false
    private var hasMore = true

    init {
        _messages.addSource(limit.switchMap { repository.selectLast(it) }) {
            _messages.value = it
            hasMore = it.size >= (limit.value ?: chunkSize)
            isLoading = false
        }
    }

    fun loadMore(index: Int = 0) {
        val currentLimit = limit.value ?: 0
        if (currentLimit >= index + chunkSize || isLoading || !hasMore) return

        isLoading = true
        limit.value = currentLimit + chunkSize
    }

    companion object {
        private const val chunkSize = 50
    }
}
