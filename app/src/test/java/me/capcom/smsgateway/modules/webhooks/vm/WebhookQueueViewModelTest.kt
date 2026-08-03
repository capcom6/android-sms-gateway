package me.capcom.smsgateway.modules.webhooks.vm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueDao
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueEntity
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueRepository
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueStatistics
import me.capcom.smsgateway.modules.webhooks.db.WebhookStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WebhookQueueViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private class FakeWebhookQueueDao : WebhookQueueDao {
        val entries = mutableListOf<WebhookQueueEntity>()
        val filteredLiveData = MutableLiveData<List<WebhookQueueEntity>>(emptyList())
        var statisticsResult = WebhookQueueStatistics(0, 0, 0, 0, 0, 0)
        var lastFilteredStatus: String? = null
        var lastFilteredLimit: Int = 0

        override fun selectLastFiltered(
            limit: Int,
            status: String?
        ): LiveData<List<WebhookQueueEntity>> {
            lastFilteredStatus = status
            lastFilteredLimit = limit
            val filtered = if (status == null) entries else entries.filter { it.status.value == status }
            filteredLiveData.value = filtered.take(limit)
            return filteredLiveData
        }

        override suspend fun getQueueStatistics(): WebhookQueueStatistics {
            return statisticsResult
        }

        override suspend fun insertWebhook(webhook: WebhookQueueEntity) = TODO()
        override suspend fun getById(id: String): WebhookQueueEntity = TODO()
        override suspend fun dueWebhooksCount(currentTime: Long): Long = TODO()
        override suspend fun getNextAttemptTime(): Long? = TODO()
        override suspend fun getPendingWebhooks(
            currentTime: Long,
            limit: Int
        ): List<WebhookQueueEntity> = TODO()
        override suspend fun markAsProcessing(id: String) = TODO()
        override suspend fun markAsFailed(id: String, nextAttempt: Long, error: String?) = TODO()
        override suspend fun markAsCompleted(id: String) = TODO()
        override suspend fun markAsPermanentlyFailed(id: String, error: String) = TODO()
        override suspend fun getOldEntryIds(cutoffTime: Long): List<String> = TODO()
        override suspend fun cleanupOldEntries(ids: List<String>) = TODO()
        override suspend fun recoverStuckProcessingWebhooks(timeoutThreshold: Long) = TODO()
    }

    private fun entity(id: String, status: WebhookStatus) = WebhookQueueEntity(
        id = id,
        url = "https://example.com/$id",
        payload = "file:$id",
        status = status
    )

    private val sampleEntries = listOf(
        entity("1", WebhookStatus.PENDING),
        entity("2", WebhookStatus.FAILED),
        entity("3", WebhookStatus.COMPLETED),
        entity("4", WebhookStatus.PROCESSING),
        entity("5", WebhookStatus.PERMANENTLY_FAILED)
    )

    private fun viewModel(entries: List<WebhookQueueEntity>): WebhookQueueViewModel {
        val dao = FakeWebhookQueueDao()
        dao.entries.addAll(entries)
        return WebhookQueueViewModel(WebhookQueueRepository(dao))
    }

    private fun observeEntries(vm: WebhookQueueViewModel): MutableList<List<WebhookQueueEntity>> {
        val observed = mutableListOf<List<WebhookQueueEntity>>()
        vm.filteredEntries.observeForever { observed.add(it) }
        return observed
    }

    @Test
    fun filter_initialNull() {
        val vm = viewModel(sampleEntries)
        assertNull(vm.filter.value)
    }

    @Test
    fun filteredEntries_initialAll_returnsAllEntries() {
        val vm = viewModel(sampleEntries)

        val observed = observeEntries(vm)

        assertEquals(sampleEntries.map { it.id }, observed.last().map { it.id })
    }

    @Test
    fun filteredEntries_selectStatus_returnsMatchingRowsOnly() {
        val vm = viewModel(sampleEntries)
        observeEntries(vm)

        vm.setFilter(WebhookStatus.FAILED)

        assertEquals(listOf("2"), vm.filteredEntries.value.orEmpty().map { it.id })
    }

    @Test
    fun filteredEntries_selectStatusWithoutMatches_returnsEmpty() {
        val vm = viewModel(
            listOf(
                entity("1", WebhookStatus.PENDING),
                entity("2", WebhookStatus.COMPLETED)
            )
        )
        observeEntries(vm)

        vm.setFilter(WebhookStatus.PERMANENTLY_FAILED)

        assertTrue(vm.filteredEntries.value.orEmpty().isEmpty())
    }

    @Test
    fun filteredEntries_reselectAll_restoresAllEntries() {
        val vm = viewModel(sampleEntries)
        observeEntries(vm)

        vm.setFilter(WebhookStatus.PROCESSING)
        vm.setFilter(null)

        assertEquals(
            sampleEntries.map { it.id },
            vm.filteredEntries.value.orEmpty().map { it.id }
        )
    }

    @Test
    fun setFilter_exposesFilterLiveData() {
        val vm = viewModel(sampleEntries)
        observeEntries(vm)
        vm.filter.observeForever { }

        vm.setFilter(WebhookStatus.COMPLETED)

        assertEquals(WebhookStatus.COMPLETED, vm.filter.value)
    }

    @Test
    fun setFilter_resetsToBoundedLimitAndStatusValue() {
        val dao = FakeWebhookQueueDao()
        dao.entries.addAll(sampleEntries)
        val vm = WebhookQueueViewModel(WebhookQueueRepository(dao))
        observeEntries(vm)

        vm.setFilter(WebhookStatus.FAILED)

        assertEquals(100, dao.lastFilteredLimit)
        assertEquals("failed", dao.lastFilteredStatus)
    }

    @Test
    fun setFilter_nullPassesNullToDao() {
        val dao = FakeWebhookQueueDao()
        dao.entries.addAll(sampleEntries)
        val vm = WebhookQueueViewModel(WebhookQueueRepository(dao))
        observeEntries(vm)

        vm.setFilter(WebhookStatus.FAILED)
        vm.setFilter(null)

        assertNull(dao.lastFilteredStatus)
    }
}
