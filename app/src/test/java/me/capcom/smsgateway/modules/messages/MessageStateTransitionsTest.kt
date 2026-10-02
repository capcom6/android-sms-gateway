package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.messages.MessageStateTransitions.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageStateTransitionsTest {

    // Spec matrix (6 states), excluding self-transitions (self is an allowed noop, no DB write).
    // Both scopes share this identical table: the gateway/server-side async-cancel
    // marker is resolved before local processing and never enters the local domain.
    private val expected: Map<ProcessingState, Set<ProcessingState>> = mapOf(
        ProcessingState.Pending to setOf(
            ProcessingState.Processed,
            ProcessingState.Sent,
            ProcessingState.Delivered,
            ProcessingState.Failed,
            ProcessingState.Cancelled
        ),
        ProcessingState.Cancelled to setOf(ProcessingState.Sent, ProcessingState.Delivered, ProcessingState.Failed),
        ProcessingState.Processed to setOf(ProcessingState.Sent, ProcessingState.Delivered, ProcessingState.Failed),
        ProcessingState.Sent to setOf(ProcessingState.Delivered, ProcessingState.Failed),
        ProcessingState.Delivered to setOf(ProcessingState.Failed),
        ProcessingState.Failed to emptySet()
    )

    @Test
    fun messageScopeCoversEveryOrderedPair() {
        assertMatrix(Scope.Message, expected)
    }

    @Test
    fun recipientScopeCoversEveryOrderedPair() {
        assertMatrix(Scope.Recipient, expected)
    }

    @Test
    fun messageAndRecipientScopesAreIdentical() {
        ProcessingState.values().forEach { from ->
            assertEquals(
                "identical scopes [$from]",
                MessageStateTransitions.allowedTransitions(from, Scope.Message),
                MessageStateTransitions.allowedTransitions(from, Scope.Recipient)
            )
        }
    }

    private fun assertMatrix(scope: Scope, expected: Map<ProcessingState, Set<ProcessingState>>) {
        ProcessingState.values().forEach { from ->
            ProcessingState.values().forEach { to ->
                val expectedValue = to == from || to in expected.getValue(from)
                assertEquals(
                    "$scope [$from -> $to]",
                    expectedValue,
                    MessageStateTransitions.canTransition(from, to, scope)
                )
            }
        }
    }

    @Test
    fun allowedTransitionsMatchesMatrixPlusSelfNoop() {
        Scope.values().forEach { scope ->
            ProcessingState.values().forEach { from ->
                assertEquals(
                    "allowedTransitions $scope [$from]",
                    expected.getValue(from) + from,
                    MessageStateTransitions.allowedTransitions(from, scope)
                )
            }
        }
    }

    @Test
    fun selfTransitionsAreNoopsInBothScopes() {
        Scope.values().forEach { scope ->
            ProcessingState.values().forEach { state ->
                assertTrue(
                    "$scope [$state -> $state]",
                    MessageStateTransitions.canTransition(state, state, scope)
                )
            }
        }
    }

    @Test
    fun failedIsTerminalAndNoOtherStateIsTerminalInBothScopes() {
        Scope.values().forEach { scope ->
            assertTrue(
                "$scope Failed",
                MessageStateTransitions.isTerminal(ProcessingState.Failed, scope)
            )
            ProcessingState.values()
                .filter { it != ProcessingState.Failed }
                .forEach { state ->
                    assertFalse(
                        "$scope $state",
                        MessageStateTransitions.isTerminal(state, scope)
                    )
                }
        }
    }

    @Test
    fun cancelledIsNotTerminalInEitherScope() {
        assertFalse(
            "Message Cancelled",
            MessageStateTransitions.isTerminal(ProcessingState.Cancelled, Scope.Message)
        )
        assertFalse(
            "Recipient Cancelled",
            MessageStateTransitions.isTerminal(ProcessingState.Cancelled, Scope.Recipient)
        )
    }

    @Test
    fun fullReachableChainPendingCancelledSentDeliveredIsValidInMessageScope() {
        val chain = listOf(
            ProcessingState.Pending,
            ProcessingState.Cancelled,
            ProcessingState.Sent,
            ProcessingState.Delivered
        )
        chain.zipWithNext().forEach { (from, to) ->
            assertTrue(
                "Message [$from -> $to]",
                MessageStateTransitions.canTransition(from, to, Scope.Message)
            )
        }
    }

    @Test
    fun cancelledLateFailureToFailedIsValidInBothScopes() {
        Scope.values().forEach { scope ->
            assertTrue(
                "$scope [Cancelled -> Failed]",
                MessageStateTransitions.canTransition(ProcessingState.Cancelled, ProcessingState.Failed, scope)
            )
        }
    }

    @Test
    fun cancelledExitsToSentAndDeliveredAreValidInBothScopes() {
        Scope.values().forEach { scope ->
            assertTrue(
                "$scope [Cancelled -> Sent]",
                MessageStateTransitions.canTransition(ProcessingState.Cancelled, ProcessingState.Sent, scope)
            )
            assertTrue(
                "$scope [Cancelled -> Delivered]",
                MessageStateTransitions.canTransition(ProcessingState.Cancelled, ProcessingState.Delivered, scope)
            )
        }
    }

    @Test
    fun cancelledExitToProcessedIsInvalidInBothScopes() {
        Scope.values().forEach { scope ->
            assertFalse(
                "$scope [Cancelled -> Processed]",
                MessageStateTransitions.canTransition(ProcessingState.Cancelled, ProcessingState.Processed, scope)
            )
        }
    }

    @Test
    fun failedHasNoOutgoingTransitionsBesidesSelfNoop() {
        Scope.values().forEach { scope ->
            assertEquals(
                "allowedTransitions $scope [Failed]",
                setOf(ProcessingState.Failed),
                MessageStateTransitions.allowedTransitions(ProcessingState.Failed, scope)
            )
        }
    }
}