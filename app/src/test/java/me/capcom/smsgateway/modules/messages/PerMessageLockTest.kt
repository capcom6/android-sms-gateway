package me.capcom.smsgateway.modules.messages

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class PerMessageLockTest {

    @Test
    fun sameKeySerializesBlocksWhileHeldByAnotherCoroutine() = runTest {
        val lock = PerMessageLock()
        val events = mutableListOf<String>()
        val firstEntered = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = launch {
            lock.withLock("msg-1") {
                events += "first-enter"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-exit"
            }
        }
        firstEntered.await()

        val second = launch {
            secondStarted.complete(Unit)
            lock.withLock("msg-1") {
                events += "second-enter"
                events += "second-exit"
            }
        }
        secondStarted.await()

        // second is waiting on the mutex held by first: second must not have entered yet
        assertEquals(listOf("first-enter"), events)
        // entry must be retained while the key is contended
        assertEquals(1, lock.activeLocks())

        releaseFirst.complete(Unit)
        first.join()
        second.join()

        assertEquals(listOf("first-enter", "first-exit", "second-enter", "second-exit"), events)
        assertEquals(0, lock.activeLocks())
    }

    @Test
    fun differentKeysDoNotBlockEachOther() = runTest {
        val lock = PerMessageLock()
        val events = mutableListOf<String>()
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

val first = launch {
            lock.withLock("msg-1") {
                events += "first-enter"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-exit"
            }
        }
        firstEntered.await()

        val second = launch {
            lock.withLock("msg-2") {
                events += "second-enter"
                secondEntered.complete(Unit)
                events += "second-exit"
            }
        }
        secondEntered.await()

        // second entered and exited its block while first still holds "msg-1": no cross-key blocking
        assertEquals(listOf("first-enter", "second-enter", "second-exit"), events)

        releaseFirst.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf("first-enter", "second-enter", "second-exit", "first-exit"), events)
        assertEquals(0, lock.activeLocks())
    }

    @Test
    fun mapStaysBoundedAfterSequentialUseOfManyKeys() = runTest {
        val lock = PerMessageLock()
        repeat(100) { i ->
            lock.withLock("key-$i") {
                // noop
            }
        }
        assertEquals(0, lock.activeLocks())

        // keys can be reused: entries are recreated on demand and released again
        repeat(100) { i ->
            lock.withLock("key-$i") {
                // noop
            }
        }
        assertEquals(0, lock.activeLocks())
    }

    @Test
    fun mapClearsAfterConcurrentUseOfDistinctKeys() = runTest {
        val lock = PerMessageLock()
        coroutineScope {
            repeat(20) { i ->
                launch {
                    lock.withLock("key-$i") {
                        // noop
                    }
                }
            }
        }
        assertEquals(0, lock.activeLocks())
    }

    @Test
    fun exceptionInBlockPropagatesAndLockIsReleased() = runTest {
        val lock = PerMessageLock()
        val thrown = runCatching {
            lock.withLock("msg-1") {
                throw IllegalStateException("boom")
            }
        }.exceptionOrNull()

        assertNotNull("exception must propagate to the caller", thrown)
        assertEquals("boom", thrown?.message)
        assertEquals(0, lock.activeLocks())

        // the same key remains usable after a failed block
        lock.withLock("msg-1") {
            // noop
        }
        assertEquals(0, lock.activeLocks())
    }

    @Test
    fun emptyKeyIsSupportedAndCleansUp() = runTest {
        val lock = PerMessageLock()
        lock.withLock("") {
            // noop
        }
        assertEquals(0, lock.activeLocks())
    }

    @Test
    fun nestedWithLockOnDifferentKeyDoesNotDeadlock() = runTest {
        val lock = PerMessageLock()
        var innerRan = false
        lock.withLock("outer-key") {
            lock.withLock("inner-key") {
                innerRan = true
            }
        }
        assertTrue(innerRan)
        assertEquals(0, lock.activeLocks())
    }

    @Test
    fun sameKeyNestingIsNotSupportedAndTimesOut() = runTest {
        // Contract: Mutex is not reentrant, so withLock on the same key inside its own
        // block is unsupported. The future service never nests same-key locks. This test
        // pins the documented no-nesting contract: nested same-key use must not enter the
        // inner block (it suspends until the virtual timeout fires).
        val lock = PerMessageLock()
        var innerEntered = false
        var timedOut = false
        try {
            withTimeout(1_000) {
                lock.withLock("same-key") {
                    lock.withLock("same-key") {
                        innerEntered = true
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            timedOut = true
        }

        assertTrue("nested same-key withLock must not be reentrant and must time out", timedOut)
        assertFalse(innerEntered)
        assertEquals(0, lock.activeLocks())
    }

    @Test
    fun callerQueuedDuringCompletionStillSerializesWithLaterCaller() = runTest {
        // Preemption-window regression: a caller that has obtained the key's entry
        // (counted as a user) but does not yet hold the mutex must keep the entry
        // alive - the completing holder cannot remove it from under the caller, and
        // a later caller must still serialize with the queued one.
        val lock = PerMessageLock()
        val events = mutableListOf<String>()
        val aEntered = CompletableDeferred<Unit>()
        val bEntered = CompletableDeferred<Unit>()
        val releaseA = CompletableDeferred<Unit>()

        val a = launch {
            lock.withLock("k") {
                events += "a-enter"
                aEntered.complete(Unit)
                releaseA.await()
                events += "a-exit"
            }
        }
        aEntered.await()

        // b arrives while a holds: b has the entry reference but not the mutex
        val b = launch {
            lock.withLock("k") {
                events += "b-enter"
                bEntered.complete(Unit)
                events += "b-exit"
            }
        }
        runCurrent()
        // b is a counted user: the entry must be retained while contended
        assertEquals(1, lock.activeLocks())

        // a completes; cleanup must NOT remove the entry from under the queued b
        releaseA.complete(Unit)
        a.join()
        b.join()
        assertEquals(listOf("a-enter", "a-exit", "b-enter", "b-exit"), events)

        // c reuses the same shared entry and serializes after b
        lock.withLock("k") {
            events += "c-enter"
            events += "c-exit"
        }
        assertEquals(
            listOf("a-enter", "a-exit", "b-enter", "b-exit", "c-enter", "c-exit"),
            events
        )
        assertEquals(0, lock.activeLocks())
    }

    @Test
    fun sameKeyNeverRunsTwoBlocksConcurrentlyUnderThreadedPreemption() =
        runBlocking(Dispatchers.Default) {
            // Hammer one key from many threads: even under real preemption between
            // entry lookup and mutex acquisition, two blocks for the same key must
            // never execute concurrently (never two holders for the same key).
            val lock = PerMessageLock()
            val concurrentHolders = AtomicInteger()
            val violations = AtomicInteger()

            val jobs = (1..32).map {
                launch {
                    repeat(2_000) {
                        lock.withLock("shared-key") {
                            val now = concurrentHolders.incrementAndGet()
                            if (now > 1) violations.incrementAndGet()
                            Thread.yield()
                            concurrentHolders.decrementAndGet()
                        }
                    }
                }
            }
            jobs.forEach { it.join() }

            assertEquals(0, violations.get())
            assertEquals(0, lock.activeLocks())
        }
}