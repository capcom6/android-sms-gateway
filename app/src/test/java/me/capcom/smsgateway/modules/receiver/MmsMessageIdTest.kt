package me.capcom.smsgateway.modules.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

internal class MmsMessageIdTest {
    @Test
    fun sameContentProducesSameId() {
        assertEquals(MmsMessageId.from(message()), MmsMessageId.from(message()))
    }

    @Test
    fun partOrderDoesNotChangeId() {
        val first = message().copy(
            attachments = listOf(part(1L, "image/jpeg", "photo.jpg"), part(2L, "text/plain", "note.txt")),
        )
        val second = message().copy(
            attachments = listOf(part(2L, "text/plain", "note.txt"), part(1L, "image/jpeg", "photo.jpg")),
        )

        assertEquals(MmsMessageId.from(first), MmsMessageId.from(second))
    }

    @Test
    fun providerLocalFieldsDoNotChangeId() {
        val original = message()
        val changed = original.copy(
            id = original.id + 1,
            attachments = original.attachments.map {
                it.copy(partId = it.partId + 100, data = "other", omitted = !it.omitted)
            },
        )

        assertEquals(MmsMessageId.from(original), MmsMessageId.from(changed))
    }

    @Test
    fun contentChangesChangeId() {
        val original = MmsMessageId.from(message())

        assertNotEquals(original, MmsMessageId.from(message().copy(sender = "+15550008888")))
        assertNotEquals(original, MmsMessageId.from(message().copy(date = Date(2_000_000_000_000L))))
        assertNotEquals(original, MmsMessageId.from(message().copy(subject = "Other")))
        assertNotEquals(original, MmsMessageId.from(message().copy(body = "Other")))
        assertNotEquals(
            original,
            MmsMessageId.from(message().copy(attachments = listOf(part(3L, "image/png", "photo.png")))),
        )
    }

    @Test
    fun delimiterLikeContentDoesNotCollide() {
        val first = message().copy(sender = "a\n3:bbb", subject = "x")
        val second = message().copy(sender = "a", subject = "3:bbb\nx")

        assertNotEquals(MmsMessageId.from(first), MmsMessageId.from(second))
    }

    @Test
    fun idHasAStablePrefixAndHexShape() {
        val id = MmsMessageId.from(message())

        assertTrue(id.startsWith("mms_"))
        assertTrue(Regex("^mms_[0-9a-f]{64}$").matches(id))
    }

    private fun message() = MmsContentReader.MmsMessage(
        id = 42L,
        sender = "+15550009999",
        subject = "Photos",
        body = "Hi!",
        date = Date(1_700_000_000_000L),
        attachments = listOf(part(3L, "image/jpeg", "photo.jpg", 38421L)),
        subscriptionId = 1,
    )

    private fun part(
        partId: Long,
        contentType: String,
        name: String? = null,
        size: Long? = 1L,
    ) = MmsContentReader.MmsAttachment(
        partId = partId,
        contentType = contentType,
        name = name,
        size = size,
        data = "BASE64",
        omitted = false,
    )
}
