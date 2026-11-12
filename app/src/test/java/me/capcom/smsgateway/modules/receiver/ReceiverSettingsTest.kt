package me.capcom.smsgateway.modules.receiver

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type

internal class ReceiverSettingsTest {
    @Test
    fun usesTheDefaultLimitWhenNotConfigured() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        assertEquals(ReceiverSettings.DEFAULT_MAX_INLINE_ATTACHMENT_BYTES, settings.mmsMaxInlineAttachmentBytes)
    }

    @Test
    fun parsesTheConfiguredLimit() {
        val storage = FakeKeyValueStorage()
        storage.set(KEY, "2048")

        assertEquals(2048L, ReceiverSettings(storage).mmsMaxInlineAttachmentBytes)
    }

    @Test
    fun zeroDisablesInlining() {
        val storage = FakeKeyValueStorage()
        storage.set(KEY, "0")

        assertEquals(0L, ReceiverSettings(storage).mmsMaxInlineAttachmentBytes)
    }

    @Test
    fun fallsBackToTheDefaultOnAnUnparsableValue() {
        val storage = FakeKeyValueStorage()
        storage.set(KEY, "not-a-number")

        assertEquals(
            ReceiverSettings.DEFAULT_MAX_INLINE_ATTACHMENT_BYTES,
            ReceiverSettings(storage).mmsMaxInlineAttachmentBytes,
        )
    }

    @Test
    fun fallsBackToTheDefaultOnAnOutOfRangeValue() {
        val storage = FakeKeyValueStorage()
        storage.set(KEY, (ReceiverSettings.MAX_INLINE_ATTACHMENT_BYTES + 1).toString())

        assertEquals(
            ReceiverSettings.DEFAULT_MAX_INLINE_ATTACHMENT_BYTES,
            ReceiverSettings(storage).mmsMaxInlineAttachmentBytes,
        )
    }

    @Test
    fun exportsTheLimitAndTheContentProviderFlag() {
        val storage = FakeKeyValueStorage()
        storage.set(KEY, "4096")

        val exported = ReceiverSettings(storage).export()

        assertEquals(4096L, exported[KEY])
        assertEquals(true, exported["content_provider_enabled"])
    }

    @Test
    fun importsTheLimit() {
        val storage = FakeKeyValueStorage()
        val settings = ReceiverSettings(storage)

        assertTrue(settings.import(mapOf(KEY to 4096)))
        assertEquals(4096L, settings.mmsMaxInlineAttachmentBytes)
    }

    @Test
    fun importsTheLimitFromAGsonDoubleValue() {
        val storage = FakeKeyValueStorage()
        val settings = ReceiverSettings(storage)

        assertTrue(settings.import(mapOf(KEY to 4096.0)))
        assertEquals(4096L, settings.mmsMaxInlineAttachmentBytes)
    }

    @Test
    fun acceptsTheDocumentedRequestValueWithoutThrowing() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        // docs/MMS.md PATCH example: {"receiver":{"mms_max_inline_attachment_bytes":1048576}}
        settings.import(mapOf(KEY to 1_048_576.0))

        assertEquals(1_048_576L, settings.mmsMaxInlineAttachmentBytes)
    }

    @Test
    fun importRejectsAFractionalLimit() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        assertThrows(IllegalArgumentException::class.java) {
            settings.import(mapOf(KEY to 100.5))
        }
    }

    @Test
    fun importReportsNoChangeForTheSameValue() {
        val settings = ReceiverSettings(FakeKeyValueStorage())
        settings.import(mapOf(KEY to 4096))

        assertFalse(settings.import(mapOf(KEY to 4096)))
    }

    @Test
    fun importRejectsAnUnparsableLimit() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        assertThrows(IllegalArgumentException::class.java) {
            settings.import(mapOf(KEY to "abc"))
        }
    }

    @Test
    fun importRejectsAnOutOfRangeLimit() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        assertThrows(IllegalArgumentException::class.java) {
            settings.import(mapOf(KEY to ReceiverSettings.MAX_INLINE_ATTACHMENT_BYTES + 1))
        }
    }

    @Test
    fun importRejectsAnUnparsableContentProviderFlag() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        assertThrows(IllegalArgumentException::class.java) {
            settings.import(mapOf("content_provider_enabled" to "yes"))
        }
    }

    private class FakeKeyValueStorage : KeyValueStorage {
        private val values = mutableMapOf<String, Any?>()

        override fun <T> set(key: String, value: T) {
            values[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, typeOfT: Type): T? {
            return values[key] as T?
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    companion object {
        private const val KEY = "mms_max_inline_attachment_bytes"
    }
}
