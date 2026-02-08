package me.stappmus.messagegateway.modules.media

import me.stappmus.messagegateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type

class MediaSettingsTest {
    @Test
    fun usesSafeDefaults() {
        val settings = MediaSettings(FakeStorage())

        assertEquals(7, settings.retentionDays)
        assertEquals(900, settings.tokenTtlSeconds)
        assertEquals(20, settings.maxAttachmentSizeMb)
        assertTrue(settings.signingKey.isNotBlank())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooSmallTtl() {
        val settings = MediaSettings(FakeStorage())

        settings.import(
            mapOf(
                MediaSettings.TOKEN_TTL_SECONDS to 30,
            )
        )
    }

    @Test
    fun exportReflectsImportedValues() {
        val settings = MediaSettings(FakeStorage())

        settings.import(
            mapOf(
                MediaSettings.RETENTION_DAYS to 14,
                MediaSettings.TOKEN_TTL_SECONDS to 1200,
                MediaSettings.MAX_ATTACHMENT_SIZE_MB to 25,
            )
        )

        val exported = settings.export()
        assertEquals(14, exported[MediaSettings.RETENTION_DAYS])
        assertEquals(1200, exported[MediaSettings.TOKEN_TTL_SECONDS])
        assertEquals(25, exported[MediaSettings.MAX_ATTACHMENT_SIZE_MB])
    }

    private class FakeStorage : KeyValueStorage {
        private val data = mutableMapOf<String, Any?>()

        override fun <T> set(key: String, value: T) {
            data[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: String, typeOfT: Type): T? {
            return data[key] as? T
        }

        override fun remove(key: String) {
            data.remove(key)
        }
    }
}
