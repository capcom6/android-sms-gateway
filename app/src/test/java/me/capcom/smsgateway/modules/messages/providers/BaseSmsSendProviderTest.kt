package me.capcom.smsgateway.modules.messages.providers

import android.app.PendingIntent
import android.content.Context
import android.telephony.SmsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.LogsSettings
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.messages.FakeMessagesDao
import me.capcom.smsgateway.modules.messages.MessageStateService
import me.capcom.smsgateway.modules.messages.MessagesSettings
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class BaseSmsSendProviderTest {

    private class Harness(
        val dao: FakeMessagesDao,
        val logDao: FakeLogEntriesDao,
        val provider: RecordingProvider,
        val events: MutableList<MessageStateChangedEvent>,
    )

    /**
     * Records the send-path calls; getSmsManager/buildPayload are the seams the
     * real Text/Data providers fill in.
     */
    private class RecordingProvider(
        context: Context,
        dao: MessagesDao,
        machine: MessageStateService,
        logsService: LogsService,
        simSelector: SimSelector,
        phoneNormalizer: PhoneNormalizer,
    ) : BaseSmsSendProvider(context, dao, machine, logsService, simSelector, phoneNormalizer) {
        val smsManagerCalls = mutableListOf<Int?>()
        val buildPayloadCalls = mutableListOf<StoredSendRequest>()
        val sendCalls = mutableListOf<String>()

        override fun supports(content: MessageContent): Boolean = true

        override fun broadcastIntent(
            action: String,
            id: String,
            phoneNumber: String,
            requestCode: Int,
        ): PendingIntent = fakePendingIntent()

        override fun getSmsManager(simNumber: Int?): SmsManager {
            smsManagerCalls += simNumber
            return fakeSmsManager()
        }

        override suspend fun buildPayload(request: StoredSendRequest, smsManager: SmsManager): SmsPayload {
            buildPayloadCalls += request
            return SmsPayload(2) { phoneNumber, _, _ -> sendCalls += phoneNumber }
        }
    }

    private fun TestScope.harness(
        simSelectionMode: MessagesSettings.SimSelectionMode = MessagesSettings.SimSelectionMode.RoundRobin,
        slots: Set<Int>? = setOf(0, 1),
    ): Harness {
        val dao = FakeMessagesDao()
        val logDao = FakeLogEntriesDao()
        val bus = EventBus()
        val machine = MessageStateService(dao, bus)
        val logsService = LogsService(logDao, LogsSettings(FakeKeyValueStorage()))
        val provider = RecordingProvider(
            fakeContext(),
            dao,
            machine,
            logsService,
            fakeSimSelector(slots, simSelectionMode),
            fakePhoneNormalizer(),
        )
        val events = mutableListOf<MessageStateChangedEvent>()
        backgroundScope.launch { bus.collect<MessageStateChangedEvent> { events += it } }
        runCurrent()
        return Harness(dao, logDao, provider, events)
    }

    private fun Harness.request(simNumber: Int? = null): StoredSendRequest {
        val recipients = dao.recipients.values.filter { it.messageId == "m1" }.toList()
        return fakeStoredRequest(
            id = 1L,
            message = fakeMessage(
                id = "m1",
                phoneNumbers = recipients.map { it.phoneNumber },
            ),
            recipients = recipients,
            params = fakeSendParams(simNumber = simNumber),
        )
    }

    private fun Harness.seed() {
        dao.seed(
            "m1",
            seedRecipients = listOf(
                "+79001234567" to ProcessingState.Pending,
                "+79001234568" to ProcessingState.Pending,
            ),
        )
    }

    @Test
    fun sendTransitionsRecipientsAndWritesSimAndPartsCount() = runTest {
        val h = harness()
        h.seed()

        val request = h.request()
        h.provider.send(request)

        assertEquals(ProcessingState.Processed, h.dao.recipientState("m1", "+79001234567"))
        assertEquals(ProcessingState.Processed, h.dao.recipientState("m1", "+79001234568"))
        assertEquals(listOf("+79001234567", "+79001234568"), h.provider.sendCalls)
        assertSame(request, h.provider.buildPayloadCalls.single())
        assertEquals(2, h.dao.storedMessage("m1")!!.partsCount)
        // RoundRobin: request id 1 over sorted slots [0, 1] -> zero-based slot 1,
        // persisted as 1-based sim number 2.
        assertEquals(listOf(1), h.provider.smsManagerCalls)
        assertEquals(2, h.dao.storedMessage("m1")!!.simNumber)
        assertEquals(2, h.events.size)
    }

    @Test
    fun explicitSimNumberIsPassedThroughZeroBasedWithoutDaoWrite() = runTest {
        val h = harness()
        h.seed()

        h.provider.send(h.request(simNumber = 3))

        assertEquals(listOf(2), h.provider.smsManagerCalls)
        assertNull(h.dao.storedMessage("m1")!!.simNumber)
        assertEquals(ProcessingState.Processed, h.dao.recipientState("m1", "+79001234567"))
    }

    @Test
    fun osDefaultSimSelectionPassesNullToSmsManager() = runTest {
        val h = harness(simSelectionMode = MessagesSettings.SimSelectionMode.OSDefault)
        h.seed()

        h.provider.send(h.request())

        assertEquals(listOf(null), h.provider.smsManagerCalls)
        assertNull(h.dao.storedMessage("m1")!!.simNumber)
        assertEquals(ProcessingState.Processed, h.dao.recipientState("m1", "+79001234567"))
    }

    @Test
    fun recipientFailureMarksFailedAndLogsError() = runTest {
        val h = harness()
        h.dao.seed(
            "m1",
            seedRecipients = listOf(
                "+79001234567" to ProcessingState.Pending,
                "abc" to ProcessingState.Pending,
            ),
        )

        h.provider.send(h.request())

        assertEquals(ProcessingState.Processed, h.dao.recipientState("m1", "+79001234567"))
        assertEquals(ProcessingState.Failed, h.dao.recipientState("m1", "abc"))
        val error = h.dao.recipients[Pair("m1", "abc")]!!.error
        assertTrue(error!!.startsWith("send: "))

        val entry = h.logDao.entries.single()
        assertEquals(LogEntry.Priority.ERROR, entry.priority)
        assertEquals("messages", entry.module)
        assertTrue(entry.message.startsWith("Can't send message: "))
    }

    @Test
    fun noSimsAvailableThrowsAndLeavesRecipientsPending() = runTest {
        val h = harness(slots = emptySet())
        h.seed()

        val ex = try {
            h.provider.send(h.request())
            null
        } catch (e: Exception) {
            e
        }

        assertTrue("expected RuntimeException but was: $ex", ex is RuntimeException)
        assertEquals("No SIMs found", ex!!.message)
        assertEquals(ProcessingState.Pending, h.dao.recipientState("m1", "+79001234567"))
        assertNull(h.dao.storedMessage("m1")!!.simNumber)
    }
}
