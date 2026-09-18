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
class HardenedPassphraseEncryptionProviderTest {
    private fun provider(passphrase: String): HardenedPassphraseEncryptionProvider {
        return HardenedPassphraseEncryptionProvider(EncryptionSettings(FakeStorage(passphrase)))
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
    fun encryptedFormatHasSeparateIvAndSalt() {
        val passphrase = "s3cret"
        val provider = provider(passphrase)

        runBlocking {
            val encrypted = provider.encrypt("payload")
            val chunks = encrypted.split('$')

            assertEquals(4, chunks.size)
            assertEquals("i=300000", chunks[0])

            val iv = Base64.decode(chunks[1], Base64.NO_WRAP)
            val salt = Base64.decode(chunks[2], Base64.NO_WRAP)
            assertEquals(16, iv.size)
            assertEquals(16, salt.size)
            assertFalse("IV must differ from salt", iv.contentEquals(salt))

            Base64.decode(chunks[3], Base64.NO_WRAP)
        }
    }

    @Test
    fun hardenedCannotDecryptLegacyOutput() {
        val passphrase = "s3cret"
        val hardened = provider(passphrase)
        val legacy = LegacyPassphraseEncryptionProvider(EncryptionSettings(FakeStorage(passphrase)))

        runBlocking {
            val legacyOutput = legacy.encrypt("payload")

            var thrown = false
            try {
                hardened.decrypt(legacyOutput)
            } catch (e: RuntimeException) {
                thrown = true
            }
            assertTrue("hardened provider must reject legacy (3-chunk) output", thrown)
        }
    }
}
