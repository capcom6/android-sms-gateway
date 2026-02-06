package me.capcom.smsgateway.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageContentMmsTest {
    @Test
    fun toStringIncludesAttachmentCountWhenTextMissing() {
        val content = MessageContent.Mms(
            text = null,
            attachments = listOf(
                MmsAttachment(
                    id = "a1",
                    mimeType = "image/jpeg",
                    filename = "photo.jpg",
                    size = 1024,
                    downloadUrl = "/media/a1",
                )
            ),
        )

        assertEquals("attachments=1", content.toString())
    }
}
