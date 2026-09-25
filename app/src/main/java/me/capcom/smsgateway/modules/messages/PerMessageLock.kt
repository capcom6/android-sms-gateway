package me.capcom.smsgateway.modules.messages

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-key mutex serialization for coroutines.
 *
 * [withLock] suspends on a [Mutex] owned by [key], so blocks for the SAME key
 * serialize while blocks for DIFFERENT keys run in parallel. The entry map is
 * backed by [ConcurrentHashMap] and is safe for concurrent use from multiple
 * dispatchers (WorkManager, receiver scopes, Ktor handlers).
 *
 * Each key maps to an [Entry] holding the mutex and a user counter. Both the
 * counter increment (get-or-create) and the decrement-remove are performed
 * atomically via [ConcurrentHashMap.compute], which serializes them under the
 * map's bin lock, so the counter cannot lose updates. Cleanup removes the
 * entry only when the counter reaches zero AND the mutex is uncontended
 * ([Mutex.tryLock] succeeds, i.e. no other coroutine is holding or waiting).
 * A caller that holds the entry reference is always already counted, so a
 * finishing caller cannot remove the entry from under it, and a released key
 * is dropped and recreated on demand, so the map stays bounded.
 *
 * NOT reentrant: nesting [withLock] for the SAME key inside its own block
 * deadlocks because [Mutex] is not reentrant. Callers must never nest
 * same-key locks; locking different keys from inside a block is supported.
 *
 * Stateless (no per-call mutable state), safe to use as a Koin single.
 */
class PerMessageLock {

    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private val locks = ConcurrentHashMap<String, Entry>()

    suspend fun <T> withLock(key: String, block: suspend () -> T): T {
        // Atomic get-or-create-and-increment under the map's bin lock: a caller
        // that holds the entry reference is already counted, so a finishing
        // caller can never remove the entry from under it.
        val entry = locks.compute(key) { _, e -> (e ?: Entry()).also { it.users++ } }!!
        return try {
            entry.mutex.withLock { block() }
        } finally {
            // Atomic decrement-and-remove under the map's bin lock: no suspension
            // inside the lambda (only Mutex.tryLock), so the removal decision
            // cannot race a concurrent acquire for the same key.
            locks.compute(key) { _, e ->
                if (e === entry) {
                    e.users--
                    if (e.users == 0 && e.mutex.tryLock()) {
                        e.mutex.unlock()
                        null
                    } else {
                        e
                    }
                } else {
                    e
                }
            }
        }
    }

    internal fun activeLocks(): Int = locks.size
}
