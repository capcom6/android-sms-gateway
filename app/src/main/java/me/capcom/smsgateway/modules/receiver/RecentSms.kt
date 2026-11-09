package me.capcom.smsgateway.modules.receiver

import kotlin.math.abs

/**
 * Remembers text SMS handled by one live path for a short time.
 *
 * The same incoming SMS can reach the app twice: through the `SMS_RECEIVED` broadcast (which
 * carries the real subscription id) and through the SMS inbox content observer (whose `sub_id`
 * column may hold the SIM slot index on some OEM builds). [LiveSmsDeduper] keeps one registry per
 * path so each path is compared only against the OTHER path.
 *
 * An entry is matched at most once: [consume] removes it, so two identical SMS never share a twin.
 */
internal class RecentSms(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Entry(
        val address: String,
        val text: String,
        val dateMs: Long,
        /** Subscription id this path posted the SMS with (null when unknown). */
        val subscriptionId: Int?,
        /** Raw provider `sub_id` (observer entries only). */
        val rawSubId: Int?,
        val recordedAtMs: Long,
    )

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun record(
        address: String,
        text: String,
        dateMs: Long,
        subscriptionId: Int?,
        rawSubId: Int? = null,
    ) {
        evictExpired()
        val duplicate = entries.any {
            it.address == address && it.text == text && it.dateMs == dateMs
                    && it.subscriptionId == subscriptionId && it.rawSubId == rawSubId
        }
        if (duplicate) {
            return
        }
        entries.addLast(Entry(address, text, dateMs, subscriptionId, rawSubId, clock()))
        while (entries.size > maxEntries) {
            entries.removeFirst()
        }
    }

    /** All live entries for the same sender, body and (nearly) the same time, oldest first. */
    @Synchronized
    fun candidates(address: String, text: String, dateMs: Long): List<Entry> {
        evictExpired()
        return entries.filter { sameMessage(it.address, it.text, it.dateMs, address, text, dateMs) }
    }

    @Synchronized
    fun consume(entry: Entry): Boolean = entries.remove(entry)

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
