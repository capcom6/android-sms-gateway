package me.capcom.smsgateway.domain

enum class ProcessingState {
    Pending,
    Cancelling,
    Cancelled,
    Processed,
    Sent,
    Delivered,
    Failed;

    /**
     * Position in the delivery progression, used to stop a late update from
     * moving a recipient backwards.
     *
     * The platform's sent/delivered broadcasts arrive on a different
     * coroutine from the send loop (see receivers/EventsReceiver), so the
     * two race. Previously the only guard on the update was
     * `state <> 'Failed'`, with no ordering rule at all — so a `Processed`
     * written after dispatch could clobber a `Sent` that had already
     * arrived, and a late `Sent` could regress an already-`Delivered`
     * recipient. With delivery reports off, a recipient clobbered back to
     * `Processed` never leaves it.
     *
     * The terminal states rank highest so they can always be applied: a
     * failure remains authoritative whenever it arrives, which is the
     * behaviour the previous guard had.
     */
    val rank: Int
        get() = when (this) {
            Pending -> 0
            Cancelling -> 1
            Processed -> 2
            Sent -> 3
            Delivered -> 4
            Cancelled -> 5
            Failed -> 5
        }
}
