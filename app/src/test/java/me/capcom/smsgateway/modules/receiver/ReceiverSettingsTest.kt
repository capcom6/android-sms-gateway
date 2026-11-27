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
    fun exportReturnsDefaultWhenUnset() {
        val exported = ReceiverSettings(FakeKeyValueStorage()).export()

        assertEquals(true, exported["content_provider_enabled"])
        assertEquals(ReceiverSettings.DEFAULT_MAX_INLINE_ATTACHMENT_BYTES, exported[KEY])
    }

    @Test
    fun importDisablesContentProvider() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        val changed = settings.import(mapOf("content_provider_enabled" to false))

        assertTrue(changed)
        assertFalse(settings.contentProviderEnabled)
        assertEquals(false, settings.export()["content_provider_enabled"])
    }

    @Test
    fun importOfSameValueReportsNoChange() {
        val settings = ReceiverSettings(FakeKeyValueStorage())
        settings.import(mapOf("content_provider_enabled" to false))

        val changed = settings.import(mapOf("content_provider_enabled" to false))

        assertFalse(changed)
    }

    @Test
    fun importOfNullValueResetsToDefault() {
        val settings = ReceiverSettings(FakeKeyValueStorage())
        settings.import(mapOf("content_provider_enabled" to false))

        val changed = settings.import(mapOf("content_provider_enabled" to null))

        assertTrue(changed)
        assertTrue(settings.contentProviderEnabled)
    }

    @Test
    fun importRejectsAnUnparsableContentProviderFlag() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        assertThrows(IllegalArgumentException::class.java) {
            settings.import(mapOf("content_provider_enabled" to "yes"))
        }
    }

    @Test
    fun importOfUnknownKeyIsIgnored() {
        assertFalse(ReceiverSettings(FakeKeyValueStorage()).import(mapOf("unknown" to 1)))
    }

    @Test
    fun defaultsToGlobalMessageIdWhenNotConfigured() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        assertTrue(settings.mmsGlobalMessageId)
    }

    @Test
    fun migrationEnablesGlobalMessageIdForFreshInstall() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        settings.migrateMmsMessageId(true)

        assertTrue(settings.mmsGlobalMessageId)
    }

    @Test
    fun migrationKeepsLegacyMessageIdForUpgradedInstall() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        settings.migrateMmsMessageId(false)

        assertFalse(settings.mmsGlobalMessageId)
    }

    @Test
    fun migrationDoesNotOverwriteAnExplicitValue() {
        val storage = FakeKeyValueStorage()
        storage.set(GLOBAL_MESSAGE_ID_KEY, false)
        val settings = ReceiverSettings(storage)

        settings.migrateMmsMessageId(true)

        assertFalse(settings.mmsGlobalMessageId)
    }

    @Test
    fun exportsAndImportsGlobalMessageId() {
        val storage = FakeKeyValueStorage()
        val settings = ReceiverSettings(storage)

        assertTrue(settings.export()[GLOBAL_MESSAGE_ID_KEY] == true)
        assertTrue(settings.import(mapOf(GLOBAL_MESSAGE_ID_KEY to false)))
        assertFalse(settings.mmsGlobalMessageId)
        assertFalse(settings.import(mapOf(GLOBAL_MESSAGE_ID_KEY to false)))
    }

    @Test
    fun importRejectsAnUnparsableGlobalMessageId() {
        val settings = ReceiverSettings(FakeKeyValueStorage())

        assertThrows(IllegalArgumentException::class.java) {
            settings.import(mapOf(GLOBAL_MESSAGE_ID_KEY to "yes"))
        }
    }

    private class FakeKeyValueStorage : KeyValueStorage {
        private val values = mutableMapOf<String, Any?>()

        override fun <T> set(key: String, value: T) {
            if (value == null) {
                values.remove(key)
            } else {
                values[key] = value
            }
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
        private const val GLOBAL_MESSAGE_ID_KEY = "mms_global_message_id"
    }
}
