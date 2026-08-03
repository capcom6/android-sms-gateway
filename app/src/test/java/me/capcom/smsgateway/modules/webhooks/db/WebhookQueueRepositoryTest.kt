package me.capcom.smsgateway.modules.webhooks.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WebhookQueueRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private class FakeWebhookQueueDao : WebhookQueueDao {
        val allEntries = mutableListOf<WebhookQueueEntity>()
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
            val filtered = if (status == null) allEntries else allEntries.filter { it.status.value == status }
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

    @Test
    fun selectLast_passesStatusValueAndBoundedLimit() {
        val dao = FakeWebhookQueueDao()
        dao.allEntries.addAll(
            listOf(
                WebhookQueueEntity(id = "1", url = "u", payload = "p", status = WebhookStatus.FAILED),
                WebhookQueueEntity(id = "2", url = "u", payload = "p", status = WebhookStatus.PENDING)
            )
        )
        val repository = WebhookQueueRepository(dao)

        repository.selectLast(50, WebhookStatus.FAILED)

        assertEquals("failed", dao.lastFilteredStatus)
        assertEquals(50, dao.lastFilteredLimit)
    }

    @Test
    fun selectLast_nullStatus_passesNullToDao() {
        val dao = FakeWebhookQueueDao()
        val repository = WebhookQueueRepository(dao)

        repository.selectLast(100, null)

        assertNull(dao.lastFilteredStatus)
    }

    @Test
    fun getQueueStatistics_delegatesToDao() = runBlocking {
        val dao = FakeWebhookQueueDao()
        val expected = WebhookQueueStatistics(
            total = 10,
            pending = 2,
            processing = 1,
            failed = 3,
            permanentlyFailed = 1,
            completed = 3
        )
        dao.statisticsResult = expected
        val repository = WebhookQueueRepository(dao)

        assertEquals(expected, repository.getQueueStatistics())
    }

    @Test
    fun canRetry_allowsBelowMaxRetries() {
        val entity = WebhookQueueEntity(
            id = "1",
            url = "https://example.com",
            payload = "file:1",
            retryCount = 2,
            status = WebhookStatus.FAILED
        )

        assertTrue(entity.canRetry(maxRetries = 3))
    }

    @Test
    fun canRetry_deniesAtMaxRetries() {
        val entity = WebhookQueueEntity(
            id = "1",
            url = "https://example.com",
            payload = "file:1",
            retryCount = 3,
            status = WebhookStatus.FAILED
        )

        assertFalse(entity.canRetry(maxRetries = 3))
    }

    @Test
    fun canRetry_deniesPermanentlyFailed() {
        val entity = WebhookQueueEntity(
            id = "1",
            url = "https://example.com",
            payload = "file:1",
            retryCount = 0,
            status = WebhookStatus.PERMANENTLY_FAILED
        )

        assertFalse(entity.canRetry(maxRetries = 3))
    }

    @Test
    fun canRetry_defaultsToThreeMaxRetries() {
        val entity = WebhookQueueEntity(
            id = "1",
            url = "https://example.com",
            payload = "file:1",
            retryCount = 3,
            status = WebhookStatus.FAILED
        )

        assertFalse(entity.canRetry())

        val entityBelowDefault = entity.copy(retryCount = 2)
        assertTrue(entityBelowDefault.canRetry())
    }

    @Test
    fun statusDisplayName_mapsAllStatuses() {
        assertEquals("Pending", WebhookStatus.PENDING.displayName)
        assertEquals("Processing", WebhookStatus.PROCESSING.displayName)
        assertEquals("Completed", WebhookStatus.COMPLETED.displayName)
        assertEquals("Failed", WebhookStatus.FAILED.displayName)
        assertEquals("Permanently Failed", WebhookStatus.PERMANENTLY_FAILED.displayName)
    }
}
