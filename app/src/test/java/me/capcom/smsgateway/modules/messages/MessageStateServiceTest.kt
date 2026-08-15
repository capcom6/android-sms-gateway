package me.capcom.smsgateway.modules.messages

import com.google.gson.GsonBuilder
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageState
import me.capcom.smsgateway.data.entities.MessageType
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.data.entities.MessagesStats
import me.capcom.smsgateway.data.entities.MessagesTotals
import me.capcom.smsgateway.data.entities.RecipientState
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory MessagesDao fake mirroring the Room SQL guard semantics exactly:
 * - updateMessageState guard: state <> 'Failed' (zero rows when already Failed)
 * - recipient updates guard: state <> 'Failed'
 * - setMessageProcessed: NO guard, sets processedAt
 * - countProcessedFrom / countFailedFrom read processedAt (health/rate-limit
 *   stats depend on it)
 * - history rows appended through the inherited interface default wrappers
 * (the fake implements only the underscored @Query primitives).
 */
internal class FakeMessagesDao : MessagesDao {

    val messages = mutableMapOf<String, Message>()
    val recipients = mutableMapOf<Pair<String, String>, MessageRecipient>()
    val messageHistory = mutableListOf<MessageState>()
    val recipientHistory = mutableListOf<RecipientState>()

    // Actual row-mutation counters (SQL-guard aware, zero-row updates excluded).
    var messageStateWrites = 0
    var recipientStateWrites = 0
    var recipientsBulkWrites = 0
    var processedWrites = 0

    fun seed(
        id: String,
        messageState: ProcessingState = ProcessingState.Pending,
        seedRecipients: List<Pair<String, ProcessingState>> = listOf("+79990000001" to ProcessingState.Pending),
        simNumber: Int? = null,
        partsCount: Int? = null,
        source: EntitySource = EntitySource.Local,
        processedAt: Long? = null
    ) {
        messages[id] = Message(
            id = id,
            withDeliveryReport = true,
            simNumber = simNumber,
            validUntil = null,
            scheduleAt = null,
            isEncrypted = false,
            skipPhoneValidation = false,
            priority = 0,
            source = source,
            type = MessageType.Text,
            // stored content must be JSON (entity serializes MessageContent)
            content = GsonBuilder().serializeNulls().create()
                .toJson(MessageContent.Text("test message")),
            state = messageState,
            partsCount = partsCount,
            createdAt = 1_000L,
            processedAt = processedAt
        )
        seedRecipients.forEach { (phone, state) ->
            this.recipients[Pair(id, phone)] = MessageRecipient(id, phone, state)
        }
    }

    fun storedState(id: String): ProcessingState? = messages[id]?.state

    fun storedMessage(id: String): Message? = messages[id]

    fun recipientState(id: String, phone: String): ProcessingState? =
        recipients[Pair(id, phone)]?.state

    //#region Reads used by the machine
    override fun get(id: String): MessageWithRecipients? {
        val message = messages[id] ?: return null
        val messageRecipients = recipients.values.filter { it.messageId == id }
        return MessageWithRecipients(message = message, recipients = messageRecipients)
    }

    override fun countProcessedFrom(timestamp: Long): MessagesStats {
        val processed = messages.values.filter { message ->
            val processedAt = message.processedAt
            processedAt != null &&
                    processedAt >= timestamp &&
                    message.state !in setOf(
                        ProcessingState.Pending,
                        ProcessingState.Cancelled,
                        ProcessingState.Failed
                    )
        }
        return MessagesStats(
            count = processed.size,
            lastTimestamp = processed.mapNotNull { it.processedAt }.maxOrNull() ?: 0L
        )
    }

    override fun countFailedFrom(timestamp: Long): MessagesStats {
        val failed = messages.values.filter { message ->
            val processedAt = message.processedAt
            message.state == ProcessingState.Failed && processedAt != null && processedAt >= timestamp
        }
        return MessagesStats(
            count = failed.size,
            lastTimestamp = failed.mapNotNull { it.processedAt }.maxOrNull() ?: 0L
        )
    }
    //#endregion

    //#region Underscored SQL primitives (guards mirror Room SQL)
    override fun _insert(message: Message) {
        messages[message.id] = message
    }

    override fun _insertRecipients(recipient: List<MessageRecipient>) {
        recipient.forEach { recipients[Pair(it.messageId, it.phoneNumber)] = it }
    }

    override fun _insertMessageState(state: MessageState) {
        messageHistory += state
    }

    override fun _insertRecipientStates(state: List<RecipientState>) {
        recipientHistory += state
    }

    override fun _insertRecipientStatesByMessage(messageId: String, state: ProcessingState) {
        recipients.values
            .filter { it.messageId == messageId }
            .forEach { recipientHistory += RecipientState(messageId, it.phoneNumber, state, System.currentTimeMillis()) }
    }

    override suspend fun _updateMessageState(id: String, state: ProcessingState) {
        val current = messages[id] ?: return
        if (current.state == ProcessingState.Failed) return
        messages[id] = current.copy(state = state)
        messageStateWrites++
    }

    override suspend fun _setMessageProcessed(id: String) {
        val current = messages[id] ?: return
        messages[id] = current.copy(
            state = ProcessingState.Processed,
            processedAt = System.currentTimeMillis()
        )
        processedWrites++
    }

    override fun _updateRecipientState(
        id: String,
        phoneNumber: String,
        state: ProcessingState,
        error: String?
    ) {
        val key = Pair(id, phoneNumber)
        val current = recipients[key] ?: return
        if (current.state == ProcessingState.Failed) return
        recipients[key] = current.copy(state = state, error = error)
        recipientStateWrites++
    }

    override fun _updateRecipientsState(
        id: String,
        state: ProcessingState,
        error: String?
    ) {
        recipients.keys.filter { it.first == id }.forEach { key ->
            val current = recipients.getValue(key)
            if (current.state != ProcessingState.Failed) {
                recipients[key] = current.copy(state = state, error = error)
                recipientsBulkWrites++
            }
        }
    }

    override fun updateSimNumber(id: String, simNumber: Int) {
        val current = messages[id] ?: return
        messages[id] = current.copy(simNumber = simNumber)
    }

    override fun updatePartsCount(id: String, partsCount: Int) {
        val current = messages[id] ?: return
        messages[id] = current.copy(partsCount = partsCount)
    }

    override suspend fun truncateLog(until: Long) = Unit
    //#endregion

    //#region Unused reads (not exercised by MessageStateService tests)
    // no initial value: distinctUntilChanged() must not emit (no Looper in
    // plain JVM tests), and no test observes the totals LiveData
    override fun getMessagesStats(): androidx.lifecycle.LiveData<MessagesTotals> =
        androidx.lifecycle.MutableLiveData()

    override fun selectLast(limit: Int): androidx.lifecycle.LiveData<List<Message>> =
        throw UnsupportedOperationException("not used")

    override fun selectLastFiltered(limit: Int, state: String?): androidx.lifecycle.LiveData<List<Message>> =
        throw UnsupportedOperationException("not used")

    override fun getPendingFifo(now: Date): MessageWithRecipients? =
        throw UnsupportedOperationException("not used")

    override fun getPendingLifo(now: Date): MessageWithRecipients? =
        throw UnsupportedOperationException("not used")

    override fun nextScheduledTime(): Long? = null

    override fun countExpeditedDue(minPriority: Byte, now: Date): Int = 0

    override fun count(
        source: EntitySource,
        state: ProcessingState?,
        start: Long,
        end: Long
    ): Int = 0

    override fun selectDescending(
        source: EntitySource,
        state: ProcessingState?,
        start: Long,
        end: Long,
        limit: Int,
        offset: Int
    ): List<MessageWithRecipients> = emptyList()

    override fun selectAscending(
        source: EntitySource,
        state: ProcessingState?,
        start: Long,
        end: Long,
        limit: Int,
        offset: Int
    ): List<MessageWithRecipients> = emptyList()
    //#endregion
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class MessageStateServiceTest {

    private class Harness(
        val dao: FakeMessagesDao,
        val bus: EventBus,
        val service: MessageStateService,
        val events: MutableList<MessageStateChangedEvent>
    )

    private fun TestScope.harness(): Harness {
        val dao = FakeMessagesDao()
        val bus = EventBus()
        val service = MessageStateService(dao, bus)
        val events = mutableListOf<MessageStateChangedEvent>()
        // backgroundScope: the infinite collector must not trip runTest's
        // UncompletedCoroutinesError (it is cancelled when the test ends).
        backgroundScope.launch {
            bus.collect<MessageStateChangedEvent> { events += it }
        }
        runCurrent()
        return Harness(dao, bus, service, events)
    }

    private fun assertNoWrites(dao: FakeMessagesDao) {
        assertEquals(0, dao.messageStateWrites)
        assertEquals(0, dao.recipientStateWrites)
        assertEquals(0, dao.recipientsBulkWrites)
        assertEquals(0, dao.processedWrites)
    }

    //#region cancel via transitionRecipients (OQ-5 STRICT REJECT)
    @Test
    fun cancelMixedPendingProcessedRecipientsThrowsWithClearMessage() = runTest {
        val h = harness()
        h.dao.seed(
            "m1",
            seedRecipients = listOf("+111" to ProcessingState.Pending, "+222" to ProcessingState.Processed)
        )

        val ex = runCatching { h.service.transitionRecipients("m1", ProcessingState.Cancelled) }
            .exceptionOrNull()

        assertTrue("cancel must throw ISE but was: $ex", ex is IllegalStateException)
        assertEquals(
            "Message with id m1 has a recipient in a state that cannot transition to Cancelled",
            ex?.message
        )
        // zero writes, zero events: the throw precedes the write cycle
        assertEquals(ProcessingState.Pending, h.dao.storedState("m1"))
        assertEquals(ProcessingState.Pending, h.dao.recipientState("m1", "+111"))
        assertEquals(ProcessingState.Processed, h.dao.recipientState("m1", "+222"))
        assertNoWrites(h.dao)
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun cancelStrictlyRejectsAnyRecipientThatCannotReachCancelled() = runTest {
        val h = harness()
        // recipients already Cancelled are self-noops and stay valid; every other
        // state that cannot reach Cancelled must reject the whole bulk cancel
        ProcessingState.values()
            .filter { !MessageStateTransitions.canTransition(it, ProcessingState.Cancelled, MessageStateTransitions.Scope.Recipient) }
            .forEach { progressed ->
                val id = "m-$progressed"
                h.dao.seed(
                    id,
                    seedRecipients = listOf("+111" to ProcessingState.Pending, "+222" to progressed)
                )

                val ex = runCatching {
                    h.service.transitionRecipients(id, ProcessingState.Cancelled)
                }.exceptionOrNull()
                assertTrue(
                    "cancel must ISE when a recipient is $progressed but was: $ex",
                    ex is IllegalStateException
                )
                assertEquals(ProcessingState.Pending, h.dao.storedState(id))
                assertEquals(ProcessingState.Pending, h.dao.recipientState(id, "+111"))
                assertEquals(progressed, h.dao.recipientState(id, "+222"))
                assertNoWrites(h.dao)
            }
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun cancelAllPendingCancelsEveryRecipientAndEmitsOneEvent() = runTest {
        val h = harness()
        h.dao.seed(
            "m1",
            simNumber = 0,
            partsCount = 4,
            source = EntitySource.Cloud,
            seedRecipients = listOf(
                "+111" to ProcessingState.Pending,
                "+222" to ProcessingState.Pending,
                "+333" to ProcessingState.Pending
            )
        )

        val result = h.service.transitionRecipients("m1", ProcessingState.Cancelled)

        assertEquals(ProcessingState.Cancelled, result.message.state)
        assertTrue(result.recipients.all { it.state == ProcessingState.Cancelled })
        assertEquals(ProcessingState.Cancelled, h.dao.storedState("m1"))
        listOf("+111", "+222", "+333").forEach {
            assertEquals(ProcessingState.Cancelled, h.dao.recipientState("m1", it))
        }
        // history rows appended for message and every recipient
        assertTrue(
            h.dao.messageHistory.any {
                it.messageId == "m1" && it.state == ProcessingState.Cancelled
            }
        )
        listOf("+111", "+222", "+333").forEach { phone ->
            assertTrue(
                h.dao.recipientHistory.any {
                    it.messageId == "m1" && it.phoneNumber == phone && it.state == ProcessingState.Cancelled
                }
            )
        }

        val event = h.events.single()
        assertEquals("m1", event.id)
        assertEquals(EntitySource.Cloud, event.source)
        assertEquals(setOf("+111", "+222", "+333"), event.phoneNumbers)
        assertEquals(ProcessingState.Cancelled, event.state)
        assertEquals(0, event.simNumber)
        assertEquals(4, event.partsCount)
        assertNull(event.error)
    }

    @Test
    fun cancelAlreadyCancelledIsNoopWithZeroWritesAndEvents() = runTest {
        val h = harness()
        h.dao.seed(
            "m1",
            messageState = ProcessingState.Cancelled,
            seedRecipients = listOf("+111" to ProcessingState.Cancelled, "+222" to ProcessingState.Cancelled)
        )

        val result = h.service.transitionRecipients("m1", ProcessingState.Cancelled)

        assertEquals(ProcessingState.Cancelled, result.message.state)
        assertEquals(2, result.recipients.size)
        assertNoWrites(h.dao)
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun cancelNoopGuardPrecedesRecipientValidation() = runTest {
        // OQ-5 STRICT REJECT: an already-Cancelled stored message that still has
        // a Pending (eligible) recipient is inconsistent and must be rejected,
        // not silently noop (the old noop-first ordering let the DELETE endpoint
        // return 200 with a still-sendable row - Greptile P1)
        val h = harness()
        h.dao.seed(
            "m1",
            messageState = ProcessingState.Cancelled,
            seedRecipients = listOf("+111" to ProcessingState.Cancelled, "+222" to ProcessingState.Pending)
        )

        val ex = runCatching { h.service.transitionRecipients("m1", ProcessingState.Cancelled) }
            .exceptionOrNull()

        assertTrue("cancel must throw ISE but was: $ex", ex is IllegalStateException)
        assertEquals(ProcessingState.Pending, h.dao.recipientState("m1", "+222"))
        assertNoWrites(h.dao)
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun missingIdThrowsIllegalArgumentException() = runTest {
        val h = harness()

        val bulkEx = runCatching {
            h.service.transitionRecipients("missing", ProcessingState.Failed)
        }.exceptionOrNull()
        assertTrue("transitionRecipients must throw IAE for missing id", bulkEx is IllegalArgumentException)

        assertNoWrites(h.dao)
        assertTrue(h.events.isEmpty())
    }
    //#endregion

    //#region Processed sync (processedAt via setMessageProcessed)
    @Test
    fun processedSyncWritesProcessedAtAndCountProcessedFromCountsTheMessage() = runTest {
        val h = harness()
        h.dao.seed("m1", seedRecipients = listOf("+111" to ProcessingState.Processed))

        h.service.syncMessageFromRecipients("m1")

        assertEquals(ProcessingState.Processed, h.dao.storedState("m1"))
        assertNotNull(h.dao.storedMessage("m1")!!.processedAt)
        assertEquals(1, h.dao.processedWrites)
        assertEquals(0, h.dao.messageStateWrites)
        // health/rate-limit stat now sees the message (was always 0 before the fix)
        assertEquals(1, h.dao.countProcessedFrom(0L).count)
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun nonProcessedDerivedSyncUsesGuardedUpdate() = runTest {
        val h = harness()
        h.dao.seed(
            "m1",
            seedRecipients = listOf("+111" to ProcessingState.Delivered, "+222" to ProcessingState.Delivered)
        )
        h.service.syncMessageFromRecipients("m1")

        assertEquals(ProcessingState.Delivered, h.dao.storedState("m1"))
        assertEquals(1, h.dao.messageStateWrites)
        assertEquals(0, h.dao.processedWrites)
        assertTrue(h.events.isEmpty())
    }
    //#endregion

    //#region Bulk transition to Failed (TTL path regression)
    @Test
    fun bulkTransitionToFailedStillWritesAllRecipientsAndEmitsError() = runTest {
        val h = harness()
        // mixed Pending + Sent: every non-Failed state can reach Failed, so the
        // strict-reject throw must not fire for the TTL path
        h.dao.seed(
            "m1",
            seedRecipients = listOf("+111" to ProcessingState.Pending, "+222" to ProcessingState.Sent)
        )

        val result = h.service.transitionRecipients("m1", ProcessingState.Failed, "TTL expired")

        listOf("+111", "+222").forEach {
            val record = requireNotNull(h.dao.recipients[Pair("m1", it)])
            assertEquals(ProcessingState.Failed, record.state)
            assertEquals("TTL expired", record.error)
        }
        assertEquals(ProcessingState.Failed, h.dao.storedState("m1"))
        assertEquals(ProcessingState.Failed, result.message.state)
        val event = h.events.single()
        assertEquals(setOf("+111", "+222"), event.phoneNumbers)
        assertEquals(ProcessingState.Failed, event.state)
        assertEquals("TTL expired", event.error)
    }
    //#endregion
}
