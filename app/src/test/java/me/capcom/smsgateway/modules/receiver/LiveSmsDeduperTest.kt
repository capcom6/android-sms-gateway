package me.capcom.smsgateway.modules.receiver

import me.capcom.smsgateway.helpers.ProviderSubIdMode
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

internal class LiveSmsDeduperTest {
    // (subscriptionId, simSlotIndex)
    private val emui = listOf(1 to 0, 3 to 1)
    private val stock = listOf(1 to 0, 2 to 1)

    private class Harness(
        active: List<Pair<Int, Int>>,
        vendorHint: Boolean,
        var mode: ProviderSubIdMode = ProviderSubIdMode.UNKNOWN,
    ) {
        val active = active
        val posted = mutableListOf<InboxMessage.Text>()
        var stored: (String, String, Long) -> List<Int?> = { _, _, _ -> emptyList() }
        val storedQueries = mutableListOf<Triple<String, String, Long>>()
        var failNextPost = false

        val deduper = LiveSmsDeduper(
            activeSubscriptions = { this.active },
            vendorUsesSlotIndex = vendorHint,
            readMode = { mode },
            writeMode = { mode = it },
            findStored = { a, t, d ->
                storedQueries += Triple(a, t, d)
                stored(a, t, d)
            },
            post = {
                if (failNextPost) {
                    failNextPost = false
                    throw IllegalStateException("boom")
                }
                posted += it
            },
            clock = { 1_000_000L },
        )

        fun broadcast(text: String, date: Long, sub: Int?, address: String = "SENDER") =
            deduper.onBroadcast(InboxMessage.Text(text, address, Date(date), sub))

        fun row(text: String, date: Long, raw: Int?, address: String = "SENDER") =
            deduper.onProviderRow(text, address, Date(date), raw)

        /** simNumber the app reports for a posted subscription id. */
        fun simNumbers() = posted.map { m -> active.find { it.first == m.subscriptionId }?.second?.plus(1) }
    }

    // ---- never lose an SMS: the same text on both SIMs ----

    @Test
    fun sameSenderAndTextOnBothSimsWithinThreeSecondsPostsTwiceWithCorrectSimNumber() {
        val h = Harness(emui, vendorHint = true)

        h.broadcast("code 1234", 10_000, sub = 1)
        h.broadcast("code 1234", 11_000, sub = 3)

        assertEquals(listOf(1, 2), h.simNumbers())
    }

    @Test
    fun theirObserverRowsAreThenSkippedOnEmuiForBothSlots() {
        listOf(
            Harness(emui, vendorHint = true),
            Harness(emui, vendorHint = false, mode = ProviderSubIdMode.SLOT_INDEX),
        ).forEach { h ->
            h.broadcast("code 1234", 10_000, sub = 1)
            h.broadcast("code 1234", 11_000, sub = 3)

            h.row("code 1234", 10_000, raw = 0)
            h.row("code 1234", 11_000, raw = 1)

            assertEquals(listOf(1, 2), h.simNumbers())
        }
    }

    @Test
    fun theirObserverRowsAreThenSkippedOnStockForBothSlots() {
        val h = Harness(stock, vendorHint = false)

        h.broadcast("code 1234", 10_000, sub = 1)
        h.broadcast("code 1234", 11_000, sub = 2)
        h.row("code 1234", 10_000, raw = 1)
        h.row("code 1234", 11_000, raw = 2)

        assertEquals(listOf(1, 2), h.simNumbers())
    }

    @Test
    fun aBroadcastIsNeverComparedWithAnotherBroadcastExceptItsOwnRedelivery() {
        val h = Harness(emui, vendorHint = true)

        // two real identical SMS on one SIM, two seconds apart: both kept
        h.broadcast("ok", 10_000, sub = 1)
        h.broadcast("ok", 12_000, sub = 1)
        assertEquals(2, h.posted.size)

        // the same broadcast delivered a second time (manifest + runtime registration)
        h.broadcast("ok", 12_000, sub = 1)
        assertEquals(2, h.posted.size)
    }

    @Test
    fun theSameTextWithTheSameTimeOnTheOtherSimIsNotARedelivery() {
        val h = Harness(emui, vendorHint = true)

        h.broadcast("ok", 10_000, sub = 1)
        h.broadcast("ok", 10_000, sub = 3)

        assertEquals(listOf(1, 2), h.simNumbers())
    }

    @Test
    fun observerFirstThenTheDoubleBroadcastDeliveryPostsOnce() {
        val h = Harness(stock, vendorHint = false)

        h.row("a", 10_000, raw = 2)
        h.broadcast("a", 10_000, sub = 2)
        h.broadcast("a", 10_000, sub = 2)

        assertEquals(1, h.posted.size)
    }

    @Test
    fun twoIdenticalSmsOnOneSimTwoSecondsApartAreBothKeptWithTheirRows() {
        val h = Harness(stock, vendorHint = false)

        h.broadcast("ok", 10_000, sub = 1)
        h.broadcast("ok", 12_000, sub = 1)
        h.row("ok", 10_000, raw = 1)
        h.row("ok", 12_000, raw = 1)

        assertEquals(2, h.posted.size)
    }

    // ---- observer only ----

    @Test
    fun observerOnlyEmuiMapsBothSlots() {
        val h = Harness(emui, vendorHint = true)

        h.row("a", 10_000, raw = 0)
        h.row("b", 20_000, raw = 1)

        assertEquals(listOf(1, 3), h.posted.map { it.subscriptionId })
        assertEquals(listOf(1, 2), h.simNumbers())
    }

    @Test
    fun observerOnlyStockKeepsTheSubscriptionId() {
        val h = Harness(stock, vendorHint = false)

        h.row("a", 10_000, raw = 1)
        h.row("b", 20_000, raw = 2)

        assertEquals(listOf(1, 2), h.posted.map { it.subscriptionId })
    }

    @Test
    fun observerRowWithoutSubIdIsPostedWithoutSubscription() {
        val h = Harness(stock, vendorHint = false)

        h.row("a", 10_000, raw = null)

        assertNull(h.posted.single().subscriptionId)
    }

    // ---- observer first ----

    @Test
    fun broadcastAfterTheObserverPostedTheSameSubscriptionIsSkipped() {
        val h = Harness(stock, vendorHint = false)

        h.row("a", 10_000, raw = 2)
        h.broadcast("a", 10_000, sub = 2)

        assertEquals(1, h.posted.size)
    }

    @Test
    fun broadcastWithAnotherSubscriptionThanTheObserverPostIsStillPosted() {
        // ids {1,3}, unlearned, no vendor hint: the observer reads slot-2 value 1 as subscription 1
        val h = Harness(emui, vendorHint = false)

        h.row("a", 10_000, raw = 1)
        h.broadcast("a", 10_000, sub = 3)

        // a duplicate is acceptable, a lost SMS is not
        assertEquals(listOf<Int?>(1, 3), h.posted.map { it.subscriptionId })
    }

    // ---- restart: registries empty, row already stored ----

    @Test
    fun storedRowWithTheSameSubscriptionSkipsTheObserverRow() {
        val h = Harness(stock, vendorHint = false)
        h.stored = { _, _, _ -> listOf(1) }

        h.row("a", 10_000, raw = 1)

        assertTrue(h.posted.isEmpty())
        assertEquals(Triple("SENDER", "a", 10_000L), h.storedQueries.single())
    }

    @Test
    fun storedRowOfTheOtherSimDoesNotSkipTheObserverRow() {
        val h = Harness(stock, vendorHint = false)
        h.stored = { _, _, _ -> listOf(2) }

        h.row("a", 10_000, raw = 1)

        assertEquals(1, h.posted.size)
    }

    @Test
    fun storedLookupUsesTheTrimmedSender() {
        val h = Harness(stock, vendorHint = false)

        h.row("a", 10_000, raw = 1, address = " SENDER ")

        assertEquals("SENDER", h.storedQueries.single().first)
    }

    // ---- null subscriptions ----

    @Test
    fun bothSidesWithoutSubscriptionAreTheSameSms() {
        val h = Harness(stock, vendorHint = false)

        h.broadcast("a", 10_000, sub = null)
        h.row("a", 10_000, raw = null)

        assertEquals(1, h.posted.size)
    }

    @Test
    fun oneSideWithoutSubscriptionIsNotADuplicate() {
        val h = Harness(stock, vendorHint = false)

        h.broadcast("a", 10_000, sub = null)
        h.row("a", 10_000, raw = 1)

        assertEquals(2, h.posted.size)
    }

    // ---- failures ----

    @Test
    fun aFailedPostIsNotRememberedSoTheObserverStillDeliversIt() {
        val h = Harness(stock, vendorHint = false)
        h.failNextPost = true

        runCatching { h.broadcast("a", 10_000, sub = 1) }
        h.row("a", 10_000, raw = 1)

        assertEquals(1, h.posted.size)
    }

    // ---- learning ----

    @Test
    fun oneInformativeTwinDoesNotChangeTheMode() {
        val h = Harness(emui, vendorHint = false)

        h.broadcast("a", 10_000, sub = 3)
        h.row("a", 10_000, raw = 1)

        assertEquals(ProviderSubIdMode.UNKNOWN, h.mode)
    }

    @Test
    fun twoAgreeingTwinsLearnSlotIndexOnEmui() {
        val h = Harness(emui, vendorHint = false)

        h.broadcast("a", 10_000, sub = 3)
        h.row("a", 10_000, raw = 1)
        h.broadcast("b", 20_000, sub = 1)
        h.row("b", 20_000, raw = 0)

        assertEquals(ProviderSubIdMode.SLOT_INDEX, h.mode)
    }

    @Test
    fun aHuaweiLikeVendorThatBehavesLikeStockIsCorrectedAfterTwoMessages() {
        val h = Harness(stock, vendorHint = true)

        // first message: the vendor hint would map value 1 to slot 1 (SIM 2), but the lone
        // broadcast on SIM 1 matches the subscription-id reading: one post, on SIM 1
        h.broadcast("a", 10_000, sub = 1)
        h.row("a", 10_000, raw = 1)
        assertEquals(ProviderSubIdMode.UNKNOWN, h.mode)
        assertEquals(listOf<Int?>(1), h.posted.map { it.subscriptionId })

        // second agreeing message: learned
        h.broadcast("b", 20_000, sub = 1)
        h.row("b", 20_000, raw = 1)
        assertEquals(ProviderSubIdMode.SUBSCRIPTION_ID, h.mode)
        assertEquals(listOf<Int?>(1, 1), h.posted.map { it.subscriptionId })
    }

    @Test
    fun huaweiThatWritesTheSubscriptionIdNeverPostsTheSmsToTheOtherSim() {
        // ids {1,3}; the vendor hint reads raw 1 as slot 1 (SIM 2) but this phone writes ids
        val h = Harness(emui, vendorHint = true)

        h.broadcast("a", 10_000, sub = 1)
        h.row("a", 10_000, raw = 1)
        h.broadcast("b", 20_000, sub = 3)
        h.row("b", 20_000, raw = 3)

        assertEquals(listOf(1, 2), h.simNumbers())
    }

    @Test
    fun nonHuaweiThatWritesTheSlotIndexNeverPostsTheSmsToTheOtherSim() {
        // ids {1,2}; without the hint raw 1 reads as subscription 1 (SIM 1) but this phone
        // writes the slot index, so raw 1 is SIM 2
        val h = Harness(stock, vendorHint = false)

        h.broadcast("a", 10_000, sub = 2)
        h.row("a", 10_000, raw = 1)
        h.broadcast("b", 20_000, sub = 1)
        h.row("b", 20_000, raw = 0)

        assertEquals(listOf(2, 1), h.simNumbers())
        assertEquals(2, h.posted.size)
    }

    @Test
    fun unlearnedGuessIsStillUsedWhenNoBroadcastWasSeen() {
        val h = Harness(emui, vendorHint = true)

        h.row("a", 10_000, raw = 1)

        assertEquals(listOf<Int?>(3), h.posted.map { it.subscriptionId })
    }

    @Test
    fun anAmbiguousTwinDoesNotLearnAndDoesNotLoseAnything() {
        val h = Harness(emui, vendorHint = false)

        // same text on both SIMs: the row has two possible twins
        h.broadcast("a", 10_000, sub = 1)
        h.broadcast("a", 10_500, sub = 3)
        h.row("a", 10_000, raw = 1)
        h.row("a", 10_500, raw = 1)

        assertEquals(ProviderSubIdMode.UNKNOWN, h.mode)
        // no SMS was lost: the two broadcasts are posted; rows only ever add duplicates
        assertTrue(h.posted.map { it.subscriptionId }.toSet().containsAll(listOf(1, 3)))
    }

    @Test
    fun uninformativeTwinsNeverLearn() {
        // subscription ids equal to the slot indexes: both readings agree
        val h = Harness(listOf(0 to 0, 1 to 1), vendorHint = false)

        repeat(3) { i ->
            h.broadcast("m$i", 10_000L * (i + 1), sub = 1)
            h.row("m$i", 10_000L * (i + 1), raw = 1)
        }

        assertEquals(ProviderSubIdMode.UNKNOWN, h.mode)
        assertEquals(3, h.posted.size)
    }

    // ---- ModeLearner ----

    @Test
    fun learnerNeedsTwoAgreeingObservations() {
        val l = ModeLearner()
        assertNull(l.observe(ProviderSubIdMode.SLOT_INDEX, ProviderSubIdMode.UNKNOWN))
        assertEquals(
            ProviderSubIdMode.SLOT_INDEX,
            l.observe(ProviderSubIdMode.SLOT_INDEX, ProviderSubIdMode.UNKNOWN)
        )
    }

    @Test
    fun learnerIgnoresUninformativeAndDisagreeingObservations() {
        val l = ModeLearner()
        assertNull(l.observe(ProviderSubIdMode.SLOT_INDEX, ProviderSubIdMode.UNKNOWN))
        assertNull(l.observe(null, ProviderSubIdMode.UNKNOWN))
        assertNull(l.observe(ProviderSubIdMode.SUBSCRIPTION_ID, ProviderSubIdMode.UNKNOWN))
        // the disagreement restarted the count
        assertNull(l.observe(ProviderSubIdMode.SLOT_INDEX, ProviderSubIdMode.UNKNOWN))
    }

    @Test
    fun learnerResetsWhenTheCurrentModeIsConfirmed() {
        val l = ModeLearner()
        assertNull(l.observe(ProviderSubIdMode.SUBSCRIPTION_ID, ProviderSubIdMode.SLOT_INDEX))
        assertNull(l.observe(ProviderSubIdMode.SLOT_INDEX, ProviderSubIdMode.SLOT_INDEX))
        assertNull(l.observe(ProviderSubIdMode.SUBSCRIPTION_ID, ProviderSubIdMode.SLOT_INDEX))
    }
}
