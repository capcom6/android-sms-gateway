package me.capcom.smsgateway.modules.mms

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayInputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MmsAttachmentStorageTest : KoinComponent {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storage: MmsAttachmentStorage by inject()

    @Test
    fun storeFromStreamWritesTheContentAndTheMetadata() {
        val messageId = "test-${UUID.randomUUID()}"
        try {
            val content = ByteArray(100_000) { (it % 256).toByte() }

            val stored = storage.storeFromStream(
                messageId,
                42L,
                "photo.jpg",
                "image/jpeg",
                ByteArrayInputStream(content),
            )

            assertEquals(42L, stored.partId)
            assertEquals("photo.jpg", stored.displayName)
            assertEquals("image/jpeg", stored.contentType)
            assertArrayEquals(content, stored.file.readBytes())

            val found = storage.find(messageId, 42L)
            assertNotNull(found)
            assertEquals("photo.jpg", found?.displayName)
            assertEquals("image/jpeg", found?.contentType)

            val listed = storage.list(messageId)
            assertEquals(1, listed.size)
            assertEquals(42L, listed.first().partId)
        } finally {
            storage.remove(messageId)
        }
    }

    @Test
    fun storeFromStreamAppliesTheDefaultNameAndContentType() {
        val messageId = "test-${UUID.randomUUID()}"
        try {
            val stored = storage.storeFromStream(
                messageId,
                7L,
                null,
                " ",
                ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            )

            assertEquals("attachment", stored.displayName)
            assertEquals("application/octet-stream", stored.contentType)
            assertArrayEquals(byteArrayOf(1, 2, 3), stored.file.readBytes())
        } finally {
            storage.remove(messageId)
        }
    }

    @Test
    fun storeFromStreamReplacesAnExistingAttachment() {
        val messageId = "test-${UUID.randomUUID()}"
        try {
            storage.storeFromStream(
                messageId,
                1L,
                "first.bin",
                "application/octet-stream",
                ByteArrayInputStream(ByteArray(10)),
            )

            val stored = storage.storeFromStream(
                messageId,
                1L,
                "second.bin",
                "application/octet-stream",
                ByteArrayInputStream(ByteArray(20) { 7 }),
            )

            assertEquals("second.bin", stored.displayName)
            assertEquals(20, stored.file.length().toInt())
            assertEquals(1, storage.list(messageId).size)
        } finally {
            storage.remove(messageId)
        }
    }

    @Test
    fun attachmentsAreStoredInTheApplicationFilesDirectory() {
        val messageId = "test-${UUID.randomUUID()}"
        try {
            val stored = storage.storeFromStream(
                messageId,
                3L,
                "file.bin",
                "application/octet-stream",
                ByteArrayInputStream(byteArrayOf(9)),
            )

            assertEquals(
                context.filesDir,
                stored.file.parentFile?.parentFile?.parentFile,
            )
        } finally {
            storage.remove(messageId)
        }
    }
}
