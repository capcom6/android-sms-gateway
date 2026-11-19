package me.capcom.smsgateway.modules.messages

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.lang.reflect.Field
import java.lang.reflect.Type
import java.util.Collections
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.LogsSettings
import me.capcom.smsgateway.modules.logs.db.LogEntriesDao
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for #476: a replacement worker (ExistingWorkPolicy.REPLACE)
 * must not send a message the previous worker is already sending.
 *
 * Choreography: worker A fetches the message, takes the send mutex and blocks
 * inside its state re-check (first dao.get). Worker B starts (the REPLACE
 * overlap), fetches the same still-Pending message and must park on the send
 * mutex instead of reaching its own state re-check. On the unfixed code B walks
 * straight into sendMessage while A is mid-send - the exact #476 window.
 *
 * JVM limitation: the send itself cannot run (no telephony), so the attempt
 * fails before the platform API and the machine records one Failed transition.
 * The test asserts the interleaving, not the radio behaviour - the on-device
 * repro from the issue stays the end-to-end gate.
 */
internal class MessagesServiceSendRaceTest {

    //#region Fakes

    /** Extends the in-memory Room fake with the latches/fetch counters this test needs. */
    private class SendRaceDao : FakeMessagesDao() {
        private val firstRecheck = CountDownLatch(1)
        val releaseRecheck = CountDownLatch(1)
        val getCalls = AtomicInteger()
        val pendingFetches = AtomicInteger()
        val statesSeenByGet = Collections.synchronizedList(mutableListOf<ProcessingState?>())

        val firstRecheckEntered: CountDownLatch get() = firstRecheck

        /** Mirrors `WHERE state = 'Pending' AND (scheduleAt IS NULL OR scheduleAt <= :now)`; counts fetches of a row. */
        override fun getPendingLifo(now: Date): MessageWithRecipients? {
            val message = messages.values.firstOrNull { candidate ->
                val scheduleAt = candidate.scheduleAt
                candidate.state == ProcessingState.Pending &&
                        (scheduleAt == null || scheduleAt.time <= now.time)
            } ?: return null
            pendingFetches.incrementAndGet()
            return MessageWithRecipients(
                message = message,
                recipients = recipients.values.filter { it.messageId == message.id },
            )
        }

        override fun get(id: String): MessageWithRecipients? {
            // The first dao.get in the whole scenario is worker A's state
            // re-check, reached while A holds the send mutex: park A there.
            val call = getCalls.getAndIncrement()
            if (call == 0) {
                firstRecheck.countDown()
                releaseRecheck.await(10, TimeUnit.SECONDS)
            }
            statesSeenByGet += messages[id]?.state
            return super.get(id)
        }
    }

    private class FakeLogDao : LogEntriesDao {
        val entries: MutableList<LogEntry> = Collections.synchronizedList(mutableListOf())

        override fun insert(entry: LogEntry) {
            entries += entry
        }

        override suspend fun selectByPeriod(from: Long, to: Long): List<LogEntry> = entries.toList()
        override fun selectLast(): LiveData<List<LogEntry>> = MutableLiveData()
        override suspend fun truncate(until: Long) = Unit
    }

    private class InMemoryKv : KeyValueStorage {
        private val map = mutableMapOf<String, Any?>()

        override fun <T> set(key: String, value: T) {
            map[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, typeOfT: Type): T? = map[key] as T?

        override fun remove(key: String) {
            map.remove(key)
        }
    }

    /**
     * MessagesService's constructor touches Android APIs (TelephonyManager), so
     * the instance is allocated without it and the fields the send loop reads
     * are injected - same Unsafe pattern as MessagesServiceTest.
     */
    private fun serviceWith(dao: SendRaceDao, logDao: FakeLogDao): MessagesService {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass
            .getDeclaredField("theUnsafe")
            .also { it.isAccessible = true }
            .get(null)

        val service = unsafeClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, MessagesService::class.java) as MessagesService

        fun set(field: String, value: Any?) {
            val f = MessagesService::class.java.getDeclaredField(field)
            val offset = unsafeClass
                .getMethod("objectFieldOffset", Field::class.java)
                .invoke(unsafe, f)
            unsafeClass
                .getMethod("putObject", Any::class.java, java.lang.Long.TYPE, Any::class.java)
                .invoke(unsafe, service, offset, value)
        }

        val machine = MessageStateService(dao, EventBus())
        set("dao", dao)
        set("machine", machine)
        set("messages", MessagesRepository(dao, machine))
        set("settings", MessagesSettings(InMemoryKv()))
        set("logsService", LogsService(logDao, LogsSettings(InMemoryKv())))
        set("sendMutex", Mutex())
        set("sendWorkerLock", Any())
        return service
    }

    private fun awaitTrue(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("timed out waiting for: $what")
            }
            Thread.sleep(10)
        }
    }

    private fun totalRecipientWrites(dao: FakeMessagesDao): Int =
        dao.recipientStateWrites + dao.recipientsBulkWrites

    //#endregion

    @Test
    fun replacementWorkerWaitsForInFlightSendInsteadOfResendingIt() = runBlocking {
        val dao = SendRaceDao()
        dao.seed(id = "msg-1", simNumber = 1)
        val logDao = FakeLogDao()
        val service = serviceWith(dao, logDao)

        val workerA = launch(Dispatchers.Default) { service.sendPendingMessages() }
        assertTrue(
            "worker A must fetch the message and block in its state re-check",
            dao.firstRecheckEntered.await(10, TimeUnit.SECONDS),
        )

        val workerB = launch(Dispatchers.Default) { service.sendPendingMessages() }
        awaitTrue("worker B must fetch the same still-Pending message") { dao.pendingFetches.get() >= 2 }

        // The #476 window: B fetched the in-flight message. With the send mutex
        // B must park before its state re-check; without it B reaches the send
        // while A is mid-send - exactly how the device sent the SMS twice.
        Thread.sleep(1_000)
        assertEquals(
            "worker B must wait on the send mutex instead of re-checking the in-flight message",
            1,
            dao.getCalls.get(),
        )

        dao.releaseRecheck.countDown()
        withTimeout(10_000) {
            workerA.join()
            workerB.join()
        }

        assertEquals("exactly one send attempt may transition the message", 1, totalRecipientWrites(dao))
        assertEquals(ProcessingState.Failed, dao.storedState("msg-1"))
        assertEquals(ProcessingState.Pending, dao.statesSeenByGet.first())
        assertEquals(
            "worker B's re-check must observe the state A left behind, not Pending",
            ProcessingState.Failed,
            dao.statesSeenByGet.last(),
        )
        assertTrue(
            "worker B must log the skip",
            logDao.entries.any { it.message == "Skipping message msg-1 in state Failed" },
        )
    }

    @Test
    fun replacementWorkerProceedsToTheNextMessageAfterSkippingTheInFlightOne() = runBlocking {
        val dao = SendRaceDao()
        dao.seed(id = "msg-x", simNumber = 1)
        dao.seed(id = "msg-y", simNumber = 1)
        val logDao = FakeLogDao()
        val service = serviceWith(dao, logDao)

        val workerA = launch(Dispatchers.Default) { service.sendPendingMessages() }
        assertTrue(
            "worker A must fetch msg-x and block in its state re-check",
            dao.firstRecheckEntered.await(10, TimeUnit.SECONDS),
        )

        val workerB = launch(Dispatchers.Default) { service.sendPendingMessages() }
        awaitTrue("worker B must fetch the same still-Pending msg-x") { dao.pendingFetches.get() >= 2 }

        Thread.sleep(1_000)
        assertEquals(
            "worker B must wait on the send mutex instead of re-checking the in-flight message",
            1,
            dao.getCalls.get(),
        )

        dao.releaseRecheck.countDown()
        withTimeout(10_000) {
            workerA.join()
            workerB.join()
        }

        // The single mutex must not strand msg-y: after skipping msg-x, the
        // workers still drain the queue between them - each message attempted
        // exactly once, no duplicate, no message left Pending.
        assertEquals("one send attempt per message", 2, totalRecipientWrites(dao))
        assertEquals(ProcessingState.Failed, dao.storedState("msg-x"))
        assertEquals(ProcessingState.Failed, dao.storedState("msg-y"))
        assertTrue(
            "at least the in-flight msg-x must be logged as skipped; the exact count" +
                " depends on scheduling (the msg-y loser may find an empty queue)",
            logDao.entries.count { it.message.startsWith("Skipping message msg-") } >= 1,
        )
    }
}
