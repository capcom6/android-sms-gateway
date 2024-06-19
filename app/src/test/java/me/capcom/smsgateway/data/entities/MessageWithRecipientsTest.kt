package me.capcom.smsgateway.data.entities

import me.capcom.smsgateway.domain.EntitySource
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageWithRecipientsTest {
    @Test
    fun testStatePending() {
        val message = Message(
            id = "1",
            text = "Test message",
            withDeliveryReport = true,
            simNumber = 1,
            validUntil = null,
            isEncrypted = false,
            source = EntitySource.Local,
            state = Message.State.Pending,
            createdAt = System.currentTimeMillis(),
            skipPhoneValidation = true,
        )
        val recipients = listOf(
            MessageRecipient("1", "1234567890", Message.State.Pending, null),
            MessageRecipient("1", "9876543210", Message.State.Processed, null)
        )
        val messageWithRecipients = MessageWithRecipients(message, recipients)

        assertEquals(Message.State.Pending, messageWithRecipients.state)
    }

    @Test
    fun testStateSent() {
        val message = Message(
            id = "1",
            text = "Test message",
            withDeliveryReport = true,
            simNumber = 1,
            validUntil = null,
            isEncrypted = false,
            source = EntitySource.Local,
            state = Message.State.Pending,
            createdAt = System.currentTimeMillis(),
            skipPhoneValidation = true,
        )
        val recipients = listOf(
            MessageRecipient("1", "1234567890", Message.State.Delivered, null),
            MessageRecipient("1", "9876543210", Message.State.Sent, null)
        )
        val messageWithRecipients = MessageWithRecipients(message, recipients)

        assertEquals(Message.State.Sent, messageWithRecipients.state)
    }

    @Test
    fun testStateDelivered() {
        val message = Message(
            id = "2",
            text = "Test message",
            withDeliveryReport = true,
            simNumber = 1,
            validUntil = null,
            isEncrypted = false,
            source = EntitySource.Local,
            state = Message.State.Pending,
            createdAt = System.currentTimeMillis(),
            skipPhoneValidation = true,
        )
        val recipients = listOf(
            MessageRecipient("2", "1234567890", Message.State.Delivered, null),
            MessageRecipient("2", "9876543210", Message.State.Delivered, null)
        )
        val messageWithRecipients = MessageWithRecipients(message, recipients)

        assertEquals(Message.State.Delivered, messageWithRecipients.state)
    }

    // Add more test cases for other states
}