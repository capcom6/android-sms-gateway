package me.capcom.smsgateway.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SubscriptionsHelperTest {
    // (subscriptionId, simSlotIndex) as on the Huawei HED-L29
    private val active = listOf(1 to 0, 3 to 1)

    @Test
    fun activeSubscriptionIdIsKept() {
        assertEquals(1, SubscriptionsHelper.normalizeProviderSubId(1, active))
        assertEquals(3, SubscriptionsHelper.normalizeProviderSubId(3, active))
    }

    @Test
    fun slotIndexIsMappedToSubscriptionId() {
        assertEquals(1, SubscriptionsHelper.normalizeProviderSubId(0, active))
        assertEquals(6, SubscriptionsHelper.normalizeProviderSubId(1, listOf(5 to 0, 6 to 1)))
    }

    @Test
    fun unknownValueIsUnchanged() {
        assertEquals(7, SubscriptionsHelper.normalizeProviderSubId(7, active))
        assertEquals(2, SubscriptionsHelper.normalizeProviderSubId(2, active))
        assertEquals(0, SubscriptionsHelper.normalizeProviderSubId(0, emptyList()))
    }

    // ---- resolveProviderSubId: stock Android (sub_id is the subscription id) ----

    // stock dual SIM: subscription ids 1 and 2 for slots 0 and 1
    private val stock = listOf(1 to 0, 2 to 1)

    @Test
    fun stockSubscriptionIdModeKeepsValueForBothSlots() {
        val mode = ProviderSubIdMode.SUBSCRIPTION_ID
        assertEquals(1, SubscriptionsHelper.resolveProviderSubId(1, stock, mode, false))
        assertEquals(2, SubscriptionsHelper.resolveProviderSubId(2, stock, mode, false))
    }

    @Test
    fun stockUnknownModeKeepsActiveSubscriptionIds() {
        val mode = ProviderSubIdMode.UNKNOWN
        assertEquals(1, SubscriptionsHelper.resolveProviderSubId(1, stock, mode, false))
        assertEquals(2, SubscriptionsHelper.resolveProviderSubId(2, stock, mode, false))
    }

    @Test
    fun subscriptionIdModeNeverRemapsEvenIfValueLooksLikeSlot() {
        // 3 SIM-less ids: value 0 is not an active id, but in this mode it is not a slot either
        assertEquals(
            0,
            SubscriptionsHelper.resolveProviderSubId(0, stock, ProviderSubIdMode.SUBSCRIPTION_ID, false)
        )
    }

    // ---- resolveProviderSubId: EMUI (sub_id is the slot index; ids 1 and 3) ----

    @Test
    fun emuiSlotIndexModeMapsBothSlots() {
        val mode = ProviderSubIdMode.SLOT_INDEX
        assertEquals(1, SubscriptionsHelper.resolveProviderSubId(0, active, mode, false))
        // value 1 is ALSO an active subscription id (slot 0) - the slot-2 collision
        assertEquals(3, SubscriptionsHelper.resolveProviderSubId(1, active, mode, false))
    }

    @Test
    fun emuiVendorHintResolvesTheCollisionWhenModeUnknown() {
        val mode = ProviderSubIdMode.UNKNOWN
        assertEquals(1, SubscriptionsHelper.resolveProviderSubId(0, active, mode, true))
        assertEquals(3, SubscriptionsHelper.resolveProviderSubId(1, active, mode, true))
    }

    @Test
    fun learnedSubscriptionIdModeOverridesVendorHint() {
        // an Honor / Huawei phone that turns out to behave like stock Android
        val mode = ProviderSubIdMode.SUBSCRIPTION_ID
        assertEquals(1, SubscriptionsHelper.resolveProviderSubId(1, stock, mode, true))
        assertEquals(2, SubscriptionsHelper.resolveProviderSubId(2, stock, mode, true))
    }

    @Test
    fun slotIndexModeKeepsUnknownAndInvalidValues() {
        val mode = ProviderSubIdMode.SLOT_INDEX
        assertEquals(3, SubscriptionsHelper.resolveProviderSubId(3, active, mode, true))
        assertEquals(7, SubscriptionsHelper.resolveProviderSubId(7, active, mode, true))
        assertEquals(1, SubscriptionsHelper.resolveProviderSubId(1, emptyList(), mode, true))
    }

    @Test
    fun nonContiguousSlotMappingUsesSlotNotListOrder() {
        // active list returned in subscription order, slots reversed
        val reversed = listOf(1 to 1, 3 to 0)
        val mode = ProviderSubIdMode.SLOT_INDEX
        assertEquals(3, SubscriptionsHelper.resolveProviderSubId(0, reversed, mode, false))
        assertEquals(1, SubscriptionsHelper.resolveProviderSubId(1, reversed, mode, false))
    }

    // ---- inferProviderSubIdMode (learn from a broadcast twin) ----

    @Test
    fun inferSlotIndexFromEmuiBothSlots() {
        // slot 0 message: broadcast sub 1, provider row 0
        assertEquals(
            ProviderSubIdMode.SLOT_INDEX,
            SubscriptionsHelper.inferProviderSubIdMode(0, 1, active)
        )
        // slot 1 message: broadcast sub 3, provider row 1
        assertEquals(
            ProviderSubIdMode.SLOT_INDEX,
            SubscriptionsHelper.inferProviderSubIdMode(1, 3, active)
        )
    }

    @Test
    fun inferSubscriptionIdFromStockWhenIdsDifferFromSlots() {
        // ids 1 and 2 for slots 0 and 1: both messages are informative
        assertEquals(
            ProviderSubIdMode.SUBSCRIPTION_ID,
            SubscriptionsHelper.inferProviderSubIdMode(1, 1, stock)
        )
        assertEquals(
            ProviderSubIdMode.SUBSCRIPTION_ID,
            SubscriptionsHelper.inferProviderSubIdMode(2, 2, stock)
        )
    }

    @Test
    fun inferIsUninformativeWhenIdEqualsSlot() {
        // subscription ids that equal the slot indices: both readings agree
        assertNull(SubscriptionsHelper.inferProviderSubIdMode(0, 0, listOf(0 to 0, 1 to 1)))
        assertNull(SubscriptionsHelper.inferProviderSubIdMode(1, 1, listOf(0 to 0, 1 to 1)))
    }

    @Test
    fun inferIsNullForUnknownSubscriptionOrUnrelatedValue() {
        assertNull(SubscriptionsHelper.inferProviderSubIdMode(0, 9, active))
        assertNull(SubscriptionsHelper.inferProviderSubIdMode(7, 3, active))
        assertNull(SubscriptionsHelper.inferProviderSubIdMode(0, 1, emptyList()))
    }

    // ---- broadcast intent extras ----

    private val slotToSub = { slot: Int -> SubscriptionsHelper.findSubscriptionId(active, slot) }

    @Test
    fun pickSubscriptionIdPrefersSubscriptionExtras() {
        assertEquals(3, SubscriptionsHelper.pickSubscriptionId(3, 1, 0, slotToSub))
        assertEquals(3, SubscriptionsHelper.pickSubscriptionId(null, 3, 0, slotToSub))
    }

    @Test
    fun pickSubscriptionIdMapsSlotExtraForBothSlots() {
        assertEquals(1, SubscriptionsHelper.pickSubscriptionId(null, null, 0, slotToSub))
        assertEquals(3, SubscriptionsHelper.pickSubscriptionId(null, null, 1, slotToSub))
    }

    @Test
    fun pickSubscriptionIdSkipsInvalidNegativeValues() {
        assertEquals(3, SubscriptionsHelper.pickSubscriptionId(-1, -1, 1, slotToSub))
        assertNull(SubscriptionsHelper.pickSubscriptionId(-1, null, -1, slotToSub))
        assertNull(SubscriptionsHelper.pickSubscriptionId(null, null, null, slotToSub))
        assertNull(SubscriptionsHelper.pickSubscriptionId(null, null, 5, slotToSub))
    }

    // ---- vendor hint ----

    @Test
    fun vendorHint() {
        assertTrue(SubscriptionsHelper.providerUsesSlotIndex("HUAWEI"))
        assertTrue(SubscriptionsHelper.providerUsesSlotIndex("Huawei"))
        assertTrue(SubscriptionsHelper.providerUsesSlotIndex("HONOR"))
        assertFalse(SubscriptionsHelper.providerUsesSlotIndex("samsung"))
        assertFalse(SubscriptionsHelper.providerUsesSlotIndex("Google"))
        assertFalse(SubscriptionsHelper.providerUsesSlotIndex(null))
        assertFalse(SubscriptionsHelper.providerUsesSlotIndex(""))
    }

    // ---- outbound: simNumber (1-based slot) -> subscription id ----

    @Test
    fun findSubscriptionIdUsesSlotIndexForBothSlots() {
        assertEquals(1, SubscriptionsHelper.findSubscriptionId(active, 0))
        assertEquals(3, SubscriptionsHelper.findSubscriptionId(active, 1))
        assertEquals(1, SubscriptionsHelper.findSubscriptionId(stock, 0))
        assertEquals(2, SubscriptionsHelper.findSubscriptionId(stock, 1))
    }

    @Test
    fun findSubscriptionIdIsIndependentOfListOrder() {
        val reversed = listOf(3 to 1, 1 to 0)
        assertEquals(1, SubscriptionsHelper.findSubscriptionId(reversed, 0))
        assertEquals(3, SubscriptionsHelper.findSubscriptionId(reversed, 1))
        // a subscription id is never mistaken for a slot index
        assertNull(SubscriptionsHelper.findSubscriptionId(active, 3))
    }

    @Test
    fun findSubscriptionIdUnknownSlot() {
        assertNull(SubscriptionsHelper.findSubscriptionId(active, 2))
        assertNull(SubscriptionsHelper.findSubscriptionId(active, -1))
        assertNull(SubscriptionsHelper.findSubscriptionId(emptyList(), 0))
    }

    @Test
    fun simNumberToSlotIndex() {
        assertEquals(0, SubscriptionsHelper.slotIndexForSimNumber(1))
        assertEquals(1, SubscriptionsHelper.slotIndexForSimNumber(2))
        assertNull(SubscriptionsHelper.slotIndexForSimNumber(0))
        assertNull(SubscriptionsHelper.slotIndexForSimNumber(-3))
        assertNull(SubscriptionsHelper.slotIndexForSimNumber(null))
    }

    @Test
    fun slotIndexToSimNumberRoundTrip() {
        assertEquals(1, SubscriptionsHelper.simNumberForSlotIndex(0))
        assertEquals(2, SubscriptionsHelper.simNumberForSlotIndex(1))
        assertNull(SubscriptionsHelper.simNumberForSlotIndex(null))
    }

    // ---- inbound/status: subscription id -> simNumber ----

    @Test
    fun simNumberForSubscriptionIdBothSlotsBothDevices() {
        assertEquals(1, SubscriptionsHelper.simNumberForSubscriptionId(active, 1))
        assertEquals(2, SubscriptionsHelper.simNumberForSubscriptionId(active, 3))
        assertEquals(1, SubscriptionsHelper.simNumberForSubscriptionId(stock, 1))
        assertEquals(2, SubscriptionsHelper.simNumberForSubscriptionId(stock, 2))
        assertNull(SubscriptionsHelper.simNumberForSubscriptionId(active, 2))
        assertNull(SubscriptionsHelper.simNumberForSubscriptionId(active, null))
    }
}
