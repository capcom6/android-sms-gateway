package me.capcom.smsgateway.modules.receiver

import me.capcom.smsgateway.helpers.ProviderSubIdMode
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import java.util.Date

/**
 * Decides which copy of a live text SMS is posted when it can arrive twice: through the
 * `SMS_RECEIVED` broadcast and through the SMS inbox observer.
 *
 * Rule: never lose an SMS; a rare duplicate is acceptable.
 *
 * - A broadcast is compared only with SMS the OBSERVER posted, never with another broadcast, so
 *   the same text arriving on both SIMs is always posted twice.
 * - Two copies are the same SMS only when sender, body and time match AND they resolve to the
 *   same subscription (both unknown counts as equal). A different or unresolved subscription
 *   means "not a duplicate": post it.
 * - A twin is used once ([RecentSms.consume]).
 * - After a restart the registries are empty; the observer then falls back to [findStored]
 *   (rows saved earlier, exact time and subscription only).
 * - The provider `sub_id` mode is learned only from a single, unambiguous twin, and only after
 *   two observations agree ([ModeLearner]).
 *
 * Everything Android-specific is injected so this class is unit-tested on the JVM.
 */
internal class LiveSmsDeduper(
    private val activeSubscriptions: () -> List<Pair<Int, Int>>,
    private val vendorUsesSlotIndex: Boolean,
    private val readMode: () -> ProviderSubIdMode,
    private val writeMode: (ProviderSubIdMode) -> Unit,
    /** Subscription ids of text SMS already stored for this sender, body and exact time. */
    private val findStored: (address: String, text: String, dateMs: Long) -> List<Int?>,
    /** Saves and emits the message (throws on failure: it is then not remembered). */
    private val post: (InboxMessage.Text) -> Unit,
    private val log: (message: String, details: Map<String, Any?>) -> Unit = { _, _ -> },
    clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val broadcasts = RecentSms(clock = clock)
    private val observerPosts = RecentSms(clock = clock)

    /**
     * Every broadcast seen, never consumed. The manifest and the runtime registration both
     * deliver each broadcast, so an identical copy (same time and subscription, the window the
     * hash id always used) is the same delivery, not a new SMS.
     */
    private val seenBroadcasts = RecentSms(clock = clock)
    private val learner = ModeLearner()

    /** `SMS_RECEIVED` (authoritative subscription id from the intent). */
    fun onBroadcast(message: InboxMessage.Text) = synchronized(lock) {
        if (seenBroadcasts.contains(
                message.address,
                message.text,
                message.date.time,
                message.subscriptionId
            )
        ) {
            log("identical broadcast already handled, skipping", details(message))
            return@synchronized
        }

        val posts = observerPosts.candidates(message.address, message.text, message.date.time)

        posts.singleOrNull()?.let { observed ->
            val raw = observed.rawSubId
            val sub = message.subscriptionId
            if (raw != null && sub != null) {
                learn(SubscriptionsHelper.inferProviderSubIdMode(raw, sub, activeSubscriptions()))
            }
        }

        val twin = posts.firstOrNull { it.subscriptionId == message.subscriptionId }
        if (twin != null) {
            observerPosts.consume(twin)
            seenBroadcasts.record(
                message.address,
                message.text,
                message.date.time,
                message.subscriptionId
            )
            log("same SMS already posted by the inbox observer, skipping broadcast", details(message))
            return@synchronized
        }

        post(message)
        seenBroadcasts.record(
            message.address,
            message.text,
            message.date.time,
            message.subscriptionId
        )
        broadcasts.record(
            message.address,
            message.text,
            message.date.time,
            message.subscriptionId
        )
    }

    /** A row of `content://sms/inbox`; [rawSubId] is the provider `sub_id` as stored. */
    fun onProviderRow(text: String, address: String, date: Date, rawSubId: Int?) =
        synchronized(lock) {
            val twins = broadcasts.candidates(address, text, date.time)

            twins.singleOrNull()?.let { twin ->
                val sub = twin.subscriptionId
                if (rawSubId != null && sub != null) {
                    learn(
                        SubscriptionsHelper.inferProviderSubIdMode(
                            rawSubId,
                            sub,
                            activeSubscriptions()
                        )
                    )
                }
            }

            val resolved = rawSubId?.let {
                SubscriptionsHelper.resolveProviderSubId(
                    it,
                    activeSubscriptions(),
                    readMode(),
                    vendorUsesSlotIndex
                )
            }

            var twin = twins.firstOrNull { it.subscriptionId == resolved }
            if (twin == null && rawSubId != null && readMode() == ProviderSubIdMode.UNKNOWN) {
                // Mode not learned yet: the guess in `resolved` may be the wrong reading, and
                // posting it would hand the SMS to the other SIM's line. A lone broadcast
                // candidate whose subscription equals EITHER reading of the raw value is the
                // twin. With a candidate per SIM (same text on both) nothing is guessed.
                val only = twins.singleOrNull()
                if (only?.subscriptionId != null
                    && only.subscriptionId in readingsOf(rawSubId)
                ) {
                    twin = only
                }
            }
            if (twin != null) {
                broadcasts.consume(twin)
                log(
                    "same SMS already posted by the broadcast, skipping inbox row",
                    mapOf("date" to date, "subscriptionId" to resolved)
                )
                return@synchronized
            }

            if (findStored(address.trim(), text, date.time).any { it == resolved }) {
                log(
                    "same SMS already stored, skipping inbox row",
                    mapOf("date" to date, "subscriptionId" to resolved)
                )
                return@synchronized
            }

            post(InboxMessage.Text(text, address, date, resolved))
            observerPosts.record(address, text, date.time, resolved, rawSubId)
        }

    /** The raw `sub_id` read as a subscription id, and read as a slot index. */
    private fun readingsOf(rawSubId: Int): Set<Int> {
        val slotReading = activeSubscriptions().find { it.second == rawSubId }?.first
        return setOfNotNull(rawSubId, slotReading)
    }

    private fun learn(inferred: ProviderSubIdMode?) {
        val learned = learner.observe(inferred, readMode()) ?: return
        writeMode(learned)
        log("learned how the SMS provider writes sub_id", mapOf("mode" to learned.name))
    }

    private fun details(message: InboxMessage) =
        mapOf("date" to message.date, "subscriptionId" to message.subscriptionId)
}

/**
 * Turns "informative" observations into a persisted mode only when two in a row agree, so one
 * mismatched pair cannot flip it. An uninformative observation (null) changes nothing.
 */
internal class ModeLearner(private val required: Int = 2) {
    private var pending: ProviderSubIdMode? = null
    private var count = 0

    /** @return the mode to persist now, or null */
    @Synchronized
    fun observe(inferred: ProviderSubIdMode?, current: ProviderSubIdMode): ProviderSubIdMode? {
        if (inferred == null || inferred == ProviderSubIdMode.UNKNOWN) {
            return null
        }
        if (inferred == current) {
            pending = null
            count = 0
            return null
        }
        if (inferred == pending) {
            count++
        } else {
            pending = inferred
            count = 1
        }
        if (count >= required) {
            pending = null
            count = 0
            return inferred
        }
        return null
    }
}
