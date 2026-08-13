package me.capcom.smsgateway.modules.incoming.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageTotals
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository

class IncomingMessagesListViewModel(
    private val repository: IncomingMessagesRepository,
) : ViewModel() {
    val totals: LiveData<IncomingMessageTotals> = repository.totals

    private val _queryParams = MutableLiveData<Pair<Int, IncomingMessageType?>>(chunkSize to null)
    val filter: LiveData<IncomingMessageType?> = _queryParams.map { it.second }

    private val _messages = MediatorLiveData<List<IncomingMessage>>()
    val messages: LiveData<List<IncomingMessage>> = _messages

    private var isLoading = false
    private var hasMore = true

    init {
        _messages.addSource(_queryParams.switchMap { (l, type) ->
            repository.selectLast(l, type)
        }) {
            _messages.value = it
            hasMore = it.size >= (_queryParams.value?.first ?: chunkSize)
            isLoading = false
        }
    }

    fun loadMore(index: Int = 0) {
        val currentLimit = _queryParams.value?.first ?: 0
        if (currentLimit >= index + chunkSize || isLoading || !hasMore) return

        isLoading = true
        _queryParams.value = (currentLimit + chunkSize) to _queryParams.value?.second
    }

    fun setFilter(type: IncomingMessageType?) {
        val current = _queryParams.value
        if (current?.second == type) return

        _queryParams.value = chunkSize to type
        hasMore = true
    }

    companion object {
        private const val chunkSize = 50
    }
}
