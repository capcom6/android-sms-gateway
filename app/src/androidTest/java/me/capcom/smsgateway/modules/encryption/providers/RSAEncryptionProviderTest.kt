package me.capcom.smsgateway.modules.encryption.providers

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.modules.device.DeviceService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@RunWith(AndroidJUnit4::class)
class RSAEncryptionProviderTest : KoinComponent {
    private val deviceService: DeviceService by inject()
    private val provider: RSAEncryptionProvider by lazy { RSAEncryptionProvider(deviceService) }

    @Test
    fun encryptDecryptRoundTrip() {
        runBlocking {
            for (plainText in listOf(
                "",
                "hello",
                "unicode: 日本語 и ελληνικά",
                "a".repeat(1000),
            )) {
                assertEquals(plainText, provider.decrypt(provider.encrypt(plainText)))
            }
        }
    }

    @Test
    fun encryptedFormatUsesCurrentKeyVersionAndRandomMaterial() {
        runBlocking {
            val key = deviceService.ensureKey()
                ?: throw AssertionError("E2E key generation failed")
            val first = provider.encrypt("payload")
            val second = provider.encrypt("payload")
            val chunks = first.split('$')

            assertEquals(5, chunks.size)
            assertEquals("v=1", chunks[0])
            assertEquals("k=${key.keyVersion}", chunks[1])
            assertEquals(256, Base64.decode(chunks[2], Base64.NO_WRAP).size)
            assertEquals(12, Base64.decode(chunks[3], Base64.NO_WRAP).size)
            assertEquals(23, Base64.decode(chunks[4], Base64.NO_WRAP).size)
            assertFalse(chunks[2].contains('\n'))
            assertFalse(chunks[2].contains('\r'))
            assertNotEquals(first, second)
        }
    }
}
