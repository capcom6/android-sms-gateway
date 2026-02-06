package me.capcom.smsgateway.modules.webhooks.payload

import me.capcom.smsgateway.domain.MmsAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class MmsReceivedPayloadTest {
    @Test
    fun defaultAttachmentsIsEmpty() {
        val payload = MmsReceivedPayload(
            messageId = "msg-id",
            phoneNumber = "+19162255887",
            simNumber = 1,
            transactionId = "tx-id",
            subject = "Subject",
            size = 1234,
            contentClass = "CONTENT_RICH",
            receivedAt = Date(),
        )

        assertTrue(payload.attachments.isEmpty())
    }

    @Test
    fun includesAttachmentMetadata() {
        val attachment = MmsAttachment(
            id = "att-1",
            mimeType = "image/jpeg",
            filename = "photo.jpg",
            size = 2048,
            width = 800,
            height = 600,
            durationMs = null,
            sha256 = "abc123",
            downloadUrl = "https://example.test/media/att-1",
        )

        val payload = MmsReceivedPayload(
            messageId = "msg-id",
            phoneNumber = "+19162255887",
            simNumber = 1,
            transactionId = "tx-id",
            subject = "Subject",
            size = 1234,
            contentClass = "CONTENT_RICH",
            attachments = listOf(attachment),
            receivedAt = Date(),
        )

        assertEquals(1, payload.attachments.size)
        assertEquals("att-1", payload.attachments.first().id)
        assertEquals("image/jpeg", payload.attachments.first().mimeType)
    }
}
