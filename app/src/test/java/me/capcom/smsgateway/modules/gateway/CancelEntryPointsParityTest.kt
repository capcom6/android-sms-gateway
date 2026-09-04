package me.capcom.smsgateway.modules.gateway

import com.google.gson.GsonBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.events.MessageCancelledEvent
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cancel-entry-point parity (t4 item 10):
 * - POLL: handleCloudCancel (top-level internal seam) - success -> cancel
 *   invoked exactly once, no patch; IAE -> Cancelled patch; ISE -> silent;
 *   already-Cancelled retry -> silent.
 * - PUSH: the gateway/EventsReceiver MessageCancelledEvent collector (lines
 *   99-118). The real receiver is not instantiable in plain JVM unit tests
 *   (Koin `get` for Context/MessagesService at construction/collect time; no
 *   Robolectric), so the collector structure is emulated 1:1 (inner IAE+ISE
 *   catches kept, outer per-collector try/catch rethrowing
 *   CancellationException) and the assertions pin the hardening (a)
 *   semantics: non-IAE/ISE exceptions cannot cancel the coroutineScope or
 *   kill sibling collectors. Real-file wiring verified by code inspection in
 *   the t4 audit artifact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CancelEntryPointsParityTest {

    private val gson = GsonBuilder().configure().create()

    private val cloudCancelWireState: GatewayApi.MessageState =
        GatewayApi.MessageState.values()
            .first { wire -> ProcessingState.values().none { local -> local.name == wire.name } }

    private fun messageJson(state: String?): String {
        val stateField = state?.let { """, "state": "$it"""" } ?: ""
        return """{"id": "m1"$stateField, "phoneNumbers": ["+1000", "+2000"]}"""
    }

    private fun cloudCancellingMessage(): GatewayApi.Message =
        gson.fromJson(messageJson(cloudCancelWireState.name), GatewayApi.Message::class.java)

    //#region POLL parity (handleCloudCancel)
    @Test
    fun pollCancelSuccessInvokesCancelExactlyOnceWithoutPatch() = runTest {
        val message = cloudCancellingMessage()
        var invoked = 0
        var patched = false
        handleCloudCancel(
            message,
            cancelMessage = { invoked++ },
            sendCancelled = { patched = true }
        )

        assertEquals(1, invoked)
        assertFalse("successful poll cancel must not report state", patched)
    }

    @Test
    fun pollCancelIaeAcksCancelledPatchWithAllRecipients() = runTest {
        val message = cloudCancellingMessage()
        var sent: GatewayApi.MessagePatchRequest? = null
        handleCloudCancel(
            message,
            cancelMessage = { throw IllegalArgumentException("Message with id m1 not found") },
            sendCancelled = { sent = it }
        )

        val patch = sent ?: throw AssertionError("expected Cancelled patch to be sent")
        assertEquals("m1", patch.id)
        assertEquals(ProcessingState.Cancelled, patch.state)
        assertEquals(listOf("+1000", "+2000"), patch.recipients.map { it.phoneNumber })
        assertTrue(patch.recipients.all { it.state == ProcessingState.Cancelled })
        assertEquals(setOf(ProcessingState.Cancelled), patch.states.keys)
    }

    @Test
    fun pollCancelIseStaysSilent() = runTest {
        val message = cloudCancellingMessage()
        var sent = false
        handleCloudCancel(
            message,
            cancelMessage = { throw IllegalStateException("Message m1 is not in Pending state") },
            sendCancelled = { sent = true }
        )

        assertFalse("ISE poll cancel must stay silent", sent)
    }

    @Test
    fun pollCancelAlreadyCancelledRetryIsSilent() = runTest {
        val message = cloudCancellingMessage()
        var invoked = 0
        var sent = false
        // machine.cancel on an already-Cancelled message returns the row (noop)
        handleCloudCancel(
            message,
            cancelMessage = { invoked++ },
            sendCancelled = { sent = true }
        )

        assertEquals("retry must still invoke the cancel path", 1, invoked)
        assertFalse("already-cancelled retry must not report state", sent)
    }
    //#endregion

    //#region PUSH parity + hardening (a) per-collector isolation
    /**
     * 1:1 emulation of the hardened gateway/EventsReceiver collector structure:
     * inner IAE+ISE catches kept, outer per-collector try/catch rethrowing
     * CancellationException.
     */
    private suspend fun runPushCollectors(
        bus: EventBus,
        cancelFn: suspend (String) -> Unit,
        onCancelledEvent: (MessageCancelledEvent) -> Unit,
        onStateEvent: (MessageStateChangedEvent) -> Unit
    ) {
        coroutineScope {
            launch {
                bus.collect<MessageCancelledEvent> { event ->
                    try {
                        try {
                            cancelFn(event.messageId)
                        } catch (_: IllegalArgumentException) {
                            // message not found locally - nothing to cancel
                        } catch (_: IllegalStateException) {
                            // message not in Pending state - already sent/cancelled
                        }
                        onCancelledEvent(event)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onCancelledEvent(event)
                    }
                }
            }
            launch {
                bus.collect<MessageStateChangedEvent> { event ->
                    if (event.source in setOf(EntitySource.Cloud, EntitySource.Gateway)) {
                        onStateEvent(event)
                    }
                }
            }
        }
    }

    @Test
    fun pushIaeAndIseAreSwallowedAndCollectorSurvives() = runTest {
        val bus = EventBus()
        var processed = 0
        var stateTriggers = 0
        backgroundScope.launch {
            runCatching {
                runPushCollectors(
                    bus,
                    cancelFn = { id ->
                        when (id) {
                            "iae" -> throw IllegalArgumentException("Message with id $id not found")
                            "ise" -> throw IllegalStateException("Message $id is not in Pending state")
                        }
                    },
                    onCancelledEvent = { processed++ },
                    onStateEvent = { stateTriggers++ }
                )
            }
        }
        runCurrent()

        bus.emit(MessageCancelledEvent("iae"))
        bus.emit(MessageCancelledEvent("ise"))
        bus.emit(MessageCancelledEvent("ok"))
        bus.emit(
            MessageStateChangedEvent(
                "m1",
                EntitySource.Cloud,
                setOf("+111"),
                ProcessingState.Cancelled,
                null,
                null,
                null
            )
        )
        runCurrent()

        assertEquals("IAE/ISE must be swallowed, collector keeps processing", 3, processed)
        assertEquals("sibling state collector must be unaffected", 1, stateTriggers)
    }

    @Test
    fun pushNonIaeIseExceptionDoesNotPropagateNorKillSiblings() = runTest {
        val bus = EventBus()
        var processed = 0
        var stateTriggers = 0
        var scopeExitedNormally = false
        backgroundScope.launch {
            runCatching {
                coroutineScope {
                    launch {
                        bus.collect<MessageCancelledEvent> { _ ->
                            try {
                                throw RuntimeException("unexpected failure")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                processed++
                            }
                        }
                    }
                    launch {
                        bus.collect<MessageStateChangedEvent> {
                            stateTriggers++
                        }
                    }
                }
                scopeExitedNormally = true
            }
        }
        runCurrent()

        // first event trips the collector; wrapper must keep it alive
        bus.emit(MessageCancelledEvent("m1"))
        bus.emit(MessageCancelledEvent("m2"))
        bus.emit(
            MessageStateChangedEvent(
                "m1",
                EntitySource.Cloud,
                setOf("+111"),
                ProcessingState.Cancelled,
                null,
                null,
                null
            )
        )
        runCurrent()

        assertEquals("collector must survive a non-IAE/ISE exception", 2, processed)
        assertEquals("sibling collector must stay alive", 1, stateTriggers)
        assertFalse("coroutineScope must not exit early", scopeExitedNormally)
    }

    @Test
    fun pushUnisolatedCollectorExceptionKillsSiblingCollectors() = runTest {
        val bus = EventBus()
        var stateTriggers = 0
        backgroundScope.launch {
            runCatching {
                coroutineScope {
                    // control: collector WITHOUT per-collector try/catch
                    launch {
                        bus.collect<MessageCancelledEvent> { _ ->
                            throw RuntimeException("unexpected failure")
                        }
                    }
                    launch {
                        bus.collect<MessageStateChangedEvent> {
                            stateTriggers++
                        }
                    }
                }
            }
        }
        runCurrent()

        runCatching { bus.emit(MessageCancelledEvent("m1")) }
        runCurrent()
        runCatching {
            bus.emit(
                MessageStateChangedEvent(
                    "m1",
                    EntitySource.Cloud,
                    setOf("+111"),
                    ProcessingState.Cancelled,
                    null,
                    null,
                    null
                )
            )
        }
        runCurrent()

        assertEquals("the failing collector must cancel the whole scope", 0, stateTriggers)
    }

    @Test
    fun pushWrapperRethrowsCancellationException() = runTest {
        val bus = EventBus()
        var propagated: Throwable? = null
        val job = launch {
            runCatching {
                bus.collect<MessageCancelledEvent> { _ ->
                    try {
                        throw CancellationException("collector cancelled")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // must never swallow cancellation
                    }
                }
            }.exceptionOrNull()?.let { propagated = it }
        }
        runCurrent()

        runCatching { bus.emit(MessageCancelledEvent("m1")) }
        job.join()

        assertTrue(
            "CancellationException must propagate through the wrapper but was: $propagated",
            propagated is CancellationException
        )
    }
    //#endregion
}
