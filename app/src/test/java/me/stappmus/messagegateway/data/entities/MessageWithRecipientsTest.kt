package me.stappmus.messagegateway.data.entities

import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.domain.ProcessingState
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageWithRecipientsTest {
    @Test
    fun testStatePending() {
        val message = Message(
            id = "1",
            content = "Test message",
            withDeliveryReport = true,
            simNumber = 1,
            validUntil = null,
            isEncrypted = false,
            source = EntitySource.Local,
            state = ProcessingState.Pending,
            createdAt = System.currentTimeMillis(),
            skipPhoneValidation = true,
            priority = Message.PRIORITY_DEFAULT,
        )
        val recipients = listOf(
            MessageRecipient("1", "1234567890", ProcessingState.Pending, null),
            MessageRecipient("1", "9876543210", ProcessingState.Processed, null)
        )
        val messageWithRecipients = MessageWithRecipients(message, recipients)

        assertEquals(ProcessingState.Pending, messageWithRecipients.state)
    }

    @Test
    fun testStateSent() {
        val message = Message(
            id = "1",
            content = "Test message",
            withDeliveryReport = true,
            simNumber = 1,
            validUntil = null,
            isEncrypted = false,
            source = EntitySource.Local,
            state = ProcessingState.Pending,
            createdAt = System.currentTimeMillis(),
            skipPhoneValidation = true,
            priority = Message.PRIORITY_DEFAULT,
        )
        val recipients = listOf(
            MessageRecipient("1", "1234567890", ProcessingState.Delivered, null),
            MessageRecipient("1", "9876543210", ProcessingState.Sent, null)
        )
        val messageWithRecipients = MessageWithRecipients(message, recipients)

        assertEquals(ProcessingState.Sent, messageWithRecipients.state)
    }

    @Test
    fun testStateDelivered() {
        val message = Message(
            id = "2",
            content = "Test message",
            withDeliveryReport = true,
            simNumber = 1,
            validUntil = null,
            isEncrypted = false,
            source = EntitySource.Local,
            state = ProcessingState.Pending,
            createdAt = System.currentTimeMillis(),
            skipPhoneValidation = true,
            priority = Message.PRIORITY_DEFAULT,
        )
        val recipients = listOf(
            MessageRecipient("2", "1234567890", ProcessingState.Delivered, null),
            MessageRecipient("2", "9876543210", ProcessingState.Delivered, null)
        )
        val messageWithRecipients = MessageWithRecipients(message, recipients)

        assertEquals(ProcessingState.Delivered, messageWithRecipients.state)
    }

    // Add more test cases for other states
}