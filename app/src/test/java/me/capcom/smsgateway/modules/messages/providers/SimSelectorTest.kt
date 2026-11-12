package me.capcom.smsgateway.modules.messages.providers

import me.capcom.smsgateway.modules.messages.MessagesSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimSelectorTest {

    private fun selector(
        mode: MessagesSettings.SimSelectionMode,
        slots: Set<Int>?,
    ) = SimSelector(fakeContext(), fakeMessagesSettings(mode)) { slots }

    @Test
    fun explicitSimNumberReturnsZeroBasedSlot() {
        assertEquals(
            2,
            selector(MessagesSettings.SimSelectionMode.OSDefault, null)
                .select(123L, fakeSendParams(simNumber = 3)),
        )
    }

    @Test
    fun explicitSimNumberShortCircuitsEmptySlots() {
        assertEquals(
            0,
            selector(MessagesSettings.SimSelectionMode.RoundRobin, emptySet())
                .select(123L, fakeSendParams(simNumber = 1)),
        )
    }

    @Test
    fun nullSlotsReturnsNull() {
        assertNull(
            selector(MessagesSettings.SimSelectionMode.RoundRobin, null)
                .select(1L, fakeSendParams()),
        )
    }

    @Test
    fun emptySlotsThrowsNoSimsFound() {
        val ex = runCatching {
            selector(MessagesSettings.SimSelectionMode.RoundRobin, emptySet())
                .select(1L, fakeSendParams())
        }.exceptionOrNull()

        assertTrue("expected RuntimeException but was: $ex", ex is RuntimeException)
        assertEquals("No SIMs found", ex!!.message)
    }

    @Test
    fun osDefaultReturnsNull() {
        assertNull(
            selector(MessagesSettings.SimSelectionMode.OSDefault, setOf(0, 1))
                .select(1L, fakeSendParams()),
        )
    }

    @Test
    fun roundRobinCyclesSortedSlots() {
        val selector = selector(MessagesSettings.SimSelectionMode.RoundRobin, setOf(2, 0, 1))

        val selected = (0L..5L).map { selector.select(it, fakeSendParams()) }

        assertEquals(listOf(0, 1, 2, 0, 1, 2), selected)
    }

    @Test
    fun randomStaysWithinSlots() {
        val selector = selector(MessagesSettings.SimSelectionMode.Random, setOf(0, 1, 2))

        val selected = (0L until 100L).map { selector.select(it, fakeSendParams()) }

        assertTrue(selected.all { it in setOf(0, 1, 2) })
    }
}
