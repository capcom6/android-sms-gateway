package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.messages.MessageStateTransitions.Scope
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent

/**
 * SINGLE POINT of message state operations. Every operation:
 *  1. acquires the per-message lock (PerMessageLock) - the full
 *     read-validate-write-sync-emit cycle runs under the lock;
 *  2. loads the current row (MessagesDao.get);
 *  3. validates the transition against MessageStateTransitions
 *     (invalid -> false/noop, zero writes, zero events; Failed terminal;
 *     Cancelled NOT terminal - Cancelled -> Sent/Delivered/Failed stay valid
 *     per recorded OQ-1 rejection, the actual SMS outcome supersedes cancel);
 *  4. applies the DAO write keeping existing SQL guards as defense-in-depth
 *     (state <> 'Failed', Pending-only cancel rowcount relies on the machine's
 *     own read + validate cycle, not the swallowed DAO rowcount);
 *  5. syncs the stored message state from the derived recipient state under
 *     the same lock (Processed branch keeps processedAt via setMessageProcessed,
 *     every sync write gated by the table - reviewer pin 1);
 *  6. emits MessageStateChangedEvent ONLY after a successful validated write
 *     (reviewer pin 2: zero-row writes emit zero events).
 *
 * cancel(id) implements the recorded OQ-5 STRICT REJECT semantics via
 * transitionRecipients(id, Cancelled):
 * - missing id           -> IllegalArgumentException
 * - stored already Cancelled AND every recipient Cancelled -> noop return
 *   (zero events, zero writes); any other stored-Cancelled row (a recipient
 *   still Pending or advanced) -> IllegalStateException (strict reject);
 * - any recipient that cannot reach Cancelled (Sent/Processed/Failed/
 *   Delivered) -> IllegalStateException (zero writes, zero events);
 *   Sent -> Cancelled is impossible again (cancel rejected first).
 * - row vanished between the machine's read and write -> IllegalArgumentException
 *   (contract parity with the legacy MessagesService.kt:108 requireNotNull ->
 *   route 404).
 *
 * DAO state-mutation methods stay public API for now (MessagesService still
 * uses them; visibility/removal is deferred to wave 4 (t4)).
 */
open class MessageStateService(
    private val dao: MessagesDao,
    private val events: EventBus,
) {
    private val lock = PerMessageLock()

    /**
     * Single-recipient transition (intent results, per-recipient send status).
     * Validates at recipient scope, writes the recipient row, then syncs the
     * stored message state from the derived recipient state under the same
     * lock. Emits an event with `setOf(phoneNumber)` on success.
     */
    suspend fun transitionRecipient(
        id: String,
        phoneNumber: String,
        target: ProcessingState,
        error: String? = null
    ): Boolean = lock.withLock(lockKey(id)) {
        val row = dao.get(id)
            ?: throw IllegalArgumentException("Message with id $id not found")

        val recipient: MessageRecipient =
            row.recipients.firstOrNull { it.phoneNumber == phoneNumber }
                ?: return@withLock false

        if (target == recipient.state) return@withLock false
        if (!MessageStateTransitions.canTransition(recipient.state, target, Scope.Recipient)) {
            return@withLock false
        }

        dao.updateRecipientState(id, phoneNumber, target, error)
        syncStoredStateUnlocked(id)

        val after = dao.get(id) ?: return@withLock false
        emit(row = after, phoneNumbers = setOf(phoneNumber), target = target, error = error)
        true
    }

    /**
     * Bulk recipient transition (TTL expiry, send-exception path - no phone,
     * cancel via Cancelled target). Requires EVERY recipient to have a
     * table-valid transition (self noops allowed); any invalid recipient
     * throws IllegalStateException with zero writes and zero events. For
     * Cancelled targets an already-Cancelled stored message noops first
     * (OQ-5 parity). Emits one event with all recipient phones.
     */
    suspend fun transitionRecipients(
        id: String,
        target: ProcessingState,
        error: String? = null
    ): MessageWithRecipients = lock.withLock(lockKey(id)) {
        val row = dao.get(id)
            ?: throw IllegalArgumentException("Message with id $id not found")

        val recipients = row.recipients
        if (recipients.isEmpty()) return@withLock row
        if (target == ProcessingState.Cancelled) {
            // OQ-5 STRICT REJECT: cancellation is only a legitimate noop when the
            // message is already fully cancelled (stored Cancelled AND every
            // recipient already Cancelled). Any other state means a recipient is
            // still eligible (Pending) or already past Pending (Sent/Processed/
            // Failed/Delivered) - reject so the DELETE endpoint surfaces an error
            // instead of 200 with an unchanged, still-sendable row.
            if (row.message.state == ProcessingState.Cancelled &&
                recipients.all { it.state == ProcessingState.Cancelled }
            ) {
                return@withLock row
            }
            if (row.message.state == ProcessingState.Cancelled ||
                recipients.any {
                    it.state != target &&
                            !MessageStateTransitions.canTransition(it.state, target, Scope.Recipient)
                }
            ) {
                throw IllegalStateException(
                    "Message with id $id has a recipient in a state that cannot transition to $target"
                )
            }
        } else if (recipients.any {
                it.state != target &&
                        !MessageStateTransitions.canTransition(it.state, target, Scope.Recipient)
            }
        ) {
            throw IllegalStateException(
                "Message with id $id has a recipient in a state that cannot transition to $target"
            )
        }
        if (recipients.all { it.state == target }) return@withLock row

        dao.updateRecipientsAndMessageState(id, target, error, target)

        val after = dao.get(id) ?: return@withLock row
        emit(
            row = after,
            phoneNumbers = after.recipients.map { it.phoneNumber }.toSet(),
            target = target,
            error = error
        )
        after
    }

    /**
     * Reconciles the stored message state from the derived recipient state
     * (exact current getMessage branches: Processed -> setMessageProcessed style
     * with processedAt; else guarded update). Every sync write is gated by the
     * transition table (reviewer pin 1: table-forbidden Cancelled -> Processed
     * is impossible). Emits nothing - reads stay silent as today.
     */
    open suspend fun syncMessageFromRecipients(id: String) {
        lock.withLock(lockKey(id)) { syncStoredStateUnlocked(id) }
    }

    /** Must be called with the per-message lock already held (no nested locking). */
    private suspend fun syncStoredStateUnlocked(id: String) {
        val row = dao.get(id) ?: return
        val derived = row.state
        val stored = row.message.state
        if (derived == stored) return
        if (!MessageStateTransitions.canTransition(stored, derived, Scope.Message)) {
            return
        }

        dao.updateMessageState(id, derived)
    }

    private suspend fun emit(
        row: MessageWithRecipients,
        phoneNumbers: Set<String>,
        target: ProcessingState,
        error: String?
    ) {
        events.emit(
            MessageStateChangedEvent(
                id = row.message.id,
                source = row.message.source,
                phoneNumbers = phoneNumbers,
                state = target,
                simNumber = row.message.simNumber,
                partsCount = row.message.partsCount,
                error = error
            )
        )
    }

    private fun lockKey(id: String) = "message-state:$id"
}