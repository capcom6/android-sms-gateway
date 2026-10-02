package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.domain.ProcessingState

/**
 * Pure state-transition table for SMS message/recipient processing states.
 *
 * Semantics:
 * - Self transitions are noops: allowed, but never require a DB write.
 * - Cancelled is NOT terminal: the actual SMS outcome supersedes a user cancel
 *   (recorded OQ-1 rejection), so Cancelled may exit to Sent / Delivered / Failed
 *   (late send-failure). The webhook consumer receives the actual state.
 * - Cancelled -> Processed is INVALID: Processed is not an actual SMS outcome.
 * - Failed is the only terminal state in both scopes; no transitions out of it.
 * - MESSAGE and RECIPIENT scopes are identical: the gateway/server-side
 *   async-cancel marker is resolved by GatewayService before local processing,
 *   so it never enters the local domain. Both scopes share the same table.
 */
object MessageStateTransitions {

    enum class Scope {
        Message,
        Recipient
    }

    private val transitionTable: Map<ProcessingState, Set<ProcessingState>> = mapOf(
        ProcessingState.Pending to setOf(
            ProcessingState.Processed,
            ProcessingState.Sent,
            ProcessingState.Delivered,
            ProcessingState.Failed,
            ProcessingState.Cancelled
        ),
        ProcessingState.Cancelled to setOf(
            ProcessingState.Sent,
            ProcessingState.Delivered,
            ProcessingState.Failed
        ),
        ProcessingState.Processed to setOf(
            ProcessingState.Sent,
            ProcessingState.Delivered,
            ProcessingState.Failed
        ),
        ProcessingState.Sent to setOf(
            ProcessingState.Delivered,
            ProcessingState.Failed
        ),
        ProcessingState.Delivered to setOf(ProcessingState.Failed),
        ProcessingState.Failed to emptySet()
    )

    /**
     * True when [to] is reachable from [from] in [scope].
     * Self transitions are allowed noops (no DB write required).
     * Throws if [from] is not covered by the table (exhaustive by construction).
     */
    fun canTransition(from: ProcessingState, to: ProcessingState, scope: Scope): Boolean =
        from == to || transitions(scope).getValue(from).contains(to)

    /**
     * All states reachable from [from] in [scope], including the self noop.
     */
    fun allowedTransitions(from: ProcessingState, scope: Scope): Set<ProcessingState> =
        transitions(scope).getValue(from) + from

    /**
     * Failed is the only terminal state in both scopes; Cancelled is NOT terminal.
     * [scope] is accepted for API symmetry; both scopes share the same terminal set.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isTerminal(state: ProcessingState, scope: Scope): Boolean =
        state == ProcessingState.Failed

    /**
     * Scope.Message and Scope.Recipient return the same table (scopes are identical).
     */
    private fun transitions(scope: Scope): Map<ProcessingState, Set<ProcessingState>> =
        when (scope) {
            Scope.Message -> transitionTable
            Scope.Recipient -> transitionTable
        }
}