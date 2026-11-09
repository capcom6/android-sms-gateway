package me.capcom.smsgateway.modules.receiver

import kotlin.math.abs

/**
 * Remembers text SMS delivered through the `SMS_RECEIVED` broadcast for a short time.
 *
 * The same incoming SMS can reach the app twice: through the broadcast (which carries the real
 * subscription id) and through the SMS inbox content observer (whose `sub_id` column may hold
 * the SIM slot index on some OEM builds). The observer uses this registry to
 * recognise a message the broadcast already handled and to learn how the provider writes
 * `sub_id` from the pair of values.
 */
internal class RecentBroadcasts(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Entry(
        val address: String,
        val text: String,
        val dateMs: Long,
        val subscriptionId: Int?,
        val recordedAtMs: Long,
    )

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun record(address: String, text: String, dateMs: Long, subscriptionId: Int?) {
        evictExpired()
        entries.addLast(Entry(address, text, dateMs, subscriptionId, clock()))
        while (entries.size > maxEntries) {
            entries.removeFirst()
        }
    }

    @Synchronized
    fun find(address: String, text: String, dateMs: Long): Entry? {
        evictExpired()
        return entries.lastOrNull { sameMessage(it.address, it.text, it.dateMs, address, text, dateMs) }
    }

    private fun evictExpired() {
        val limit = clock() - ttlMs
        while (entries.isNotEmpty() && entries.first().recordedAtMs < limit) {
            entries.removeFirst()
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 10 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES = 64

        /**
         * The broadcast and the provider row carry the SMSC timestamp, which can differ by a
         * few hundred milliseconds between the two sources on some devices.
         */
        const val DATE_TOLERANCE_MS = 3_000L

        fun sameMessage(
            addressA: String,
            textA: String,
            dateMsA: Long,
            addressB: String,
            textB: String,
            dateMsB: Long,
        ): Boolean {
            return addressA.trim().equals(addressB.trim(), ignoreCase = true)
                    && textA == textB
                    && abs(dateMsA - dateMsB) <= DATE_TOLERANCE_MS
        }
    }
}
