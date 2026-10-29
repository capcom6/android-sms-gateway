package me.capcom.smsgateway.modules.receiver

import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type

class ReceiverSettingsTest {
    private val storage = InMemoryStorage()
    private val settings = ReceiverSettings(storage)

    @Test
    fun exportReturnsDefaultWhenUnset() {
        assertEquals(mapOf("content_provider_enabled" to true), settings.export())
    }

    @Test
    fun importDisablesContentProvider() {
        val changed = settings.import(mapOf("content_provider_enabled" to false))

        assertTrue(changed)
        assertFalse(settings.contentProviderEnabled)
        assertEquals(mapOf("content_provider_enabled" to false), settings.export())
    }

    @Test
    fun importOfSameValueReportsNoChange() {
        settings.import(mapOf("content_provider_enabled" to false))

        val changed = settings.import(mapOf("content_provider_enabled" to false))

        assertFalse(changed)
    }

    @Test
    fun importOfNullValueResetsToDefault() {
        settings.import(mapOf("content_provider_enabled" to false))

        val changed = settings.import(mapOf("content_provider_enabled" to null))

        assertTrue(changed)
        assertTrue(settings.contentProviderEnabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importOfInvalidValueFails() {
        settings.import(mapOf("content_provider_enabled" to "yes"))
    }

    @Test
    fun importOfUnknownKeyIsIgnored() {
        assertFalse(settings.import(mapOf("unknown" to 1)))
    }

    private class InMemoryStorage : KeyValueStorage {
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
}
