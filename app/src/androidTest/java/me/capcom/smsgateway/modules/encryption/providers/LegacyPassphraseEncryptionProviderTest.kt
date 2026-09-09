package me.capcom.smsgateway.modules.encryption.providers

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyPassphraseEncryptionProviderTest {
    private fun provider(passphrase: String): LegacyPassphraseEncryptionProvider {
        return LegacyPassphraseEncryptionProvider(EncryptionSettings(FakeStorage(passphrase)))
    }

    @Test
    fun encryptDecryptRoundTrip() {
        val passphrase = "correct horse battery staple"
        val provider = provider(passphrase)

        for (plain in listOf(
            "",
            "hello",
            "unicode: 日本語 и ελληνικά",
            "a".repeat(1000),
        )) {
            runBlocking {
                assertEquals(plain, provider.decrypt(provider.encrypt(plain)))
            }
        }
    }

    @Test
    fun encryptedFormatHasExpectedChunks() {
        val passphrase = "s3cret"
        val provider = provider(passphrase)

        runBlocking {
            val encrypted = provider.encrypt("payload")
            val chunks = encrypted.split('$')

            assertEquals(3, chunks.size)
            assertEquals("i=300000", chunks[0])

            val salt = Base64.decode(chunks[1], Base64.NO_WRAP)
            assertEquals(16, salt.size)

            Base64.decode(chunks[2], Base64.NO_WRAP)
        }
    }

    @Test
    fun legacyCannotDecryptHardenedOutput() {
        val passphrase = "s3cret"
        val legacy = provider(passphrase)
        val hardened = HardenedPassphraseEncryptionProvider(EncryptionSettings(FakeStorage(passphrase)))

        runBlocking {
            val hardenedOutput = hardened.encrypt("payload")

            var thrown = false
            try {
                legacy.decrypt(hardenedOutput)
            } catch (e: RuntimeException) {
                thrown = true
            }
            assertTrue("legacy provider must reject hardened (4-chunk) output", thrown)
        }
    }
}
