package me.capcom.smsgateway.modules.webhooks

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

internal object WebhookQueueCoordinator {
    internal val mutex = Mutex()

    private val finalized = AtomicBoolean(false)

    internal fun markStarted() {
        finalized.set(false)
    }

    internal fun markFinalized() {
        finalized.set(true)
    }

    internal fun isFinalized(): Boolean = finalized.get()
}
