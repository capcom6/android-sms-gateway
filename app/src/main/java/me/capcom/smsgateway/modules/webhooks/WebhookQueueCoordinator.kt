package me.capcom.smsgateway.modules.webhooks

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object WebhookQueueCoordinator {
    internal val mutex = Mutex()

    private var generation = 0L
    private var finalized = false

    internal suspend fun begin(): Long = mutex.withLock {
        generation++
        finalized = false
        generation
    }

    internal fun isCurrent(token: Long): Boolean = generation == token

    internal fun finalize(token: Long) {
        if (isCurrent(token)) {
            finalized = true
        }
    }

    internal fun isFinalized(): Boolean = finalized
}
