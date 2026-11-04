package me.capcom.smsgateway.modules.incoming

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository
import me.capcom.smsgateway.modules.mms.MmsAttachmentStorage
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayInputStream
import java.util.Date

@RunWith(AndroidJUnit4::class)
class IncomingMessagesServiceTest : KoinComponent {
    private val service: IncomingMessagesService by inject()
    private val repository: IncomingMessagesRepository by inject()
    private val storage: MmsAttachmentStorage by inject()

    @Test
    fun attachmentIsFoundByProviderIdAndByInboxId() {
        val providerId = "987654321"
        val content = byteArrayOf(1, 2, 3, 4, 5)
        val date = Date(1_234_567_890_123L)
        val message = InboxMessage.MMS(
            messageId = providerId,
            body = "hello",
            subject = null,
            attachments = emptyList(),
            address = "+15550001111",
            date = date,
            subscriptionId = null,
        )

        val saved = service.save(message)
        try {
            assertEquals("mms:" + providerId.hashCode(), saved.id)

            storage.storeFromStream(
                saved.id,
                42L,
                "photo.jpg",
                "image/jpeg",
                ByteArrayInputStream(content),
            )

            val byInboxId = service.findAttachment(saved.id, 42L)
            assertNotNull(byInboxId)
            assertArrayEquals(content, byInboxId?.file?.readBytes())

            val byProviderId = service.findAttachment(providerId, 42L)
            assertNotNull(byProviderId)
            assertArrayEquals(content, byProviderId?.file?.readBytes())

            assertNull(service.findAttachment(providerId, 43L))
            assertNull(service.findAttachment("111222333", 42L))
            assertNull(service.findAttachment("mms:111222333", 42L))
        } finally {
            storage.remove(saved.id)
            repository.delete(date.time - 1, date.time + 1, setOf(IncomingMessageType.MMS_DOWNLOADED))
        }
    }

    @Test
    fun attachmentIsNotPersistedWhenProviderPartIsMissing() {
        val providerId = "123456789"
        val missingPartId = 999_999_999L
        val date = Date(1_234_567_890_123L)
        val message = InboxMessage.MMS(
            messageId = providerId,
            body = "hello",
            subject = null,
            attachments = listOf(
                InboxMessage.MMS.Attachment(
                    partId = missingPartId,
                    contentType = "image/jpeg",
                    name = "photo.jpg",
                    size = 5L,
                    data = "AQIDBAU",
                ),
            ),
            address = "+15550001111",
            date = date,
            subscriptionId = null,
        )

        val saved = service.save(message)
        try {
            assertNull(service.findAttachment(saved.id, missingPartId))
            assertNull(service.findAttachment(providerId, missingPartId))
        } finally {
            storage.remove(saved.id)
            repository.delete(date.time - 1, date.time + 1, setOf(IncomingMessageType.MMS_DOWNLOADED))
        }
    }
}
