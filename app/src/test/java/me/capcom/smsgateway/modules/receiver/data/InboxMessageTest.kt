package me.capcom.smsgateway.modules.receiver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Date

class InboxMessageTest {
    @Test
    fun receivedAtIsNotPartOfIdentity() {
        val scts = Date(1_700_000_000_000L)

        val first = InboxMessage.Text(
            text = "hello",
            address = "+123456789",
            date = scts,
            receivedAt = Date(1_700_000_000_500L),
            subscriptionId = 1,
        )
        val second = InboxMessage.Text(
            text = "hello",
            address = "+123456789",
            date = scts,
            receivedAt = Date(1_700_000_013_000L),
            subscriptionId = 1,
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun dateIsPartOfIdentity() {
        val first = InboxMessage.Text(
            text = "hello",
            address = "+123456789",
            date = Date(1_700_000_000_000L),
            receivedAt = Date(1_700_000_000_000L),
            subscriptionId = 1,
        )
        val second = InboxMessage.Text(
            text = "hello",
            address = "+123456789",
            date = Date(1_700_000_013_000L),
            receivedAt = Date(1_700_000_013_000L),
            subscriptionId = 1,
        )

        assertNotEquals(first, second)
    }

    @Test
    fun receivedAtIsExposed() {
        val receivedAt = Date(1_700_000_000_000L)
        val message = InboxMessage.Text(
            text = "hello",
            address = "+123456789",
            date = Date(1_600_000_000_000L),
            receivedAt = receivedAt,
            subscriptionId = null,
        )

        assertEquals(receivedAt, message.receivedAt)
    }
}
