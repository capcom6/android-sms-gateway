package me.capcom.smsgateway.modules.encryption.providers

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Type

@RunWith(AndroidJUnit4::class)
class PassphraseEncryptionProviderTest {
    private class FakeStorage(private val passphrase: String) : KeyValueStorage {
        override fun <T> set(key: String, value: T) {}
        override fun <T> get(key: String, typeOfT: Type): T? {
            @Suppress("UNCHECKED_CAST")
            return when (key) {
                "passphrase" -> passphrase as T
                else -> null
            }
        }

        override fun remove(key: String) {}
    }

    private fun provider(passphrase: String): PassphraseEncryptionProvider {
        return PassphraseEncryptionProvider(EncryptionSettings(FakeStorage(passphrase)))
    }

    @Test
    fun encryptDecryptRoundTrip() = runBlocking {
        val passphrase = "correct horse battery staple"
        val provider = provider(passphrase)

        for (plain in listOf(
            "",
            "hello",
            "unicode: 日本語 и ελληνικά",
            "a".repeat(1000),
        )) {
            assertEquals(plain, provider.decrypt(provider.encrypt(plain)))
        }
    }

    @Test
    fun encryptedFormatHasExpectedChunks() = runBlocking {
        val passphrase = "s3cret"
        val provider = provider(passphrase)

        val encrypted = provider.encrypt("payload")
        val chunks = encrypted.split('$')

        assertEquals(3, chunks.size)
        assertEquals("i=300000", chunks[0])

        val salt = Base64.decode(chunks[1], Base64.NO_WRAP)
        assertEquals(16, salt.size)

        Base64.decode(chunks[2], Base64.NO_WRAP)
    }
}
