package me.capcom.smsgateway.modules.webhooks.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueEntity
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueRepository
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueStatistics
import me.capcom.smsgateway.modules.webhooks.db.WebhookStatus

class WebhookQueueViewModel(
    private val repository: WebhookQueueRepository
) : ViewModel() {

    private val _queryParams = MutableLiveData<Pair<Int, WebhookStatus?>>(CHUNK_SIZE to null)
    val filter: LiveData<WebhookStatus?> = _queryParams.map { it.second }

    private val _filteredEntries = MediatorLiveData<List<WebhookQueueEntity>>()
    val filteredEntries: LiveData<List<WebhookQueueEntity>> = _filteredEntries

    private val _queueStatistics = MutableLiveData<WebhookQueueStatistics?>(null)
    val queueStatistics: LiveData<WebhookQueueStatistics?> = _queueStatistics

    init {
        _filteredEntries.addSource(_queryParams.switchMap { (limit, status) ->
            repository.selectLast(limit, status)
        }) {
            _filteredEntries.value = it
        }
    }

    /**
     * Apply a status filter. Null selects the "All" filter.
     * Resets the list back to the bounded limit.
     */
    fun setFilter(status: WebhookStatus?) {
        _queryParams.value = CHUNK_SIZE to status
    }

    /**
     * Load the queue statistics summary. Called from the fragment lifecycle.
     */
    suspend fun refreshStatistics() {
        _queueStatistics.value = repository.getQueueStatistics()
    }

    companion object {
        private const val CHUNK_SIZE = 100
    }
}
