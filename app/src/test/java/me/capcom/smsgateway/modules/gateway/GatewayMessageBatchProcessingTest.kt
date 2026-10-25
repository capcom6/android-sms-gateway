package me.capcom.smsgateway.modules.gateway

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T7 per-item processing loop semantics (the loop formerly inlined in
 * GatewayService.getNewMessages).
 *
 * Proves:
 * - a per-item failure is reported via onError and the remaining siblings
 *   still process (isolation kept from the old catch block);
 * - CancellationException is NOT swallowed by the per-item catch (the old
 *   `catch (th: Throwable)` at GatewayService.kt:237-250 swallowed it) and
 *   stops the batch immediately;
 * - empty batches are a no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GatewayMessageBatchProcessingTest {

    private fun msg(id: String): GatewayApi.Message = GatewayApi.Message(
        id = id,
        _textMessage = GatewayApi.MessageContent.Text("text $id"),
        _dataMessage = null,
        _mmsMessage = null,
        phoneNumbers = listOf("+1000"),
        simNumber = null,
        withDeliveryReport = null,
        isEncrypted = null,
        validUntil = null,
        scheduleAt = null,
        priority = null,
        state = null,
        createdAt = null,
        _message = null,
    )

    @Test
    fun processesEverySiblingInOrder() = runTest {
        val processed = mutableListOf<String>()
        val errors = mutableListOf<String>()

        processMessageBatch(
            listOf(msg("m1"), msg("m2"), msg("m3")),
            process = { processed += it.id },
            onError = { m, _ -> errors += m.id }
        )

        assertEquals(listOf("m1", "m2", "m3"), processed)
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun itemErrorIsReportedAndSiblingsStillProcess() = runTest {
        val processed = mutableListOf<String>()
        val errors = mutableListOf<Pair<String, Throwable>>()

        processMessageBatch(
            listOf(msg("m1"), msg("m2"), msg("m3")),
            process = { message ->
                processed += message.id
                if (message.id == "m2") throw IllegalStateException("insert failed for m2")
            },
            onError = { m, th -> errors += m.id to th }
        )

        assertEquals(
            "an item error must not abort the batch",
            listOf("m1", "m2", "m3"),
            processed
        )
        assertEquals(listOf("m2"), errors.map { it.first })
        assertEquals("insert failed for m2", errors.single().second.message)
    }

    @Test
    fun cancellationExceptionPropagatesAndStopsBatch() = runTest {
        val processed = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var propagated: Throwable? = null

        try {
            processMessageBatch(
                listOf(msg("m1"), msg("m2"), msg("m3")),
                process = { message ->
                    processed += message.id
                    if (message.id == "m2") throw CancellationException("pull cancelled")
                },
                onError = { m, _ -> errors += m.id }
            )
        } catch (e: CancellationException) {
            propagated = e
        }

        assertTrue(
            "CancellationException must propagate out of the per-item catch, was: $propagated",
            propagated is CancellationException
        )
        assertEquals(
            "batch must stop at cancellation",
            listOf("m1", "m2"),
            processed
        )
        assertEquals(
            "cancellation must not be logged as an item error",
            emptyList<String>(),
            errors
        )
    }

    @Test
    fun emptyBatchProcessesNothing() = runTest {
        var processed = 0
        var errored = 0

        processMessageBatch(
            emptyList(),
            process = { processed++ },
            onError = { _, _ -> errored++ }
        )

        assertEquals(0, processed)
        assertEquals(0, errored)
    }
}
