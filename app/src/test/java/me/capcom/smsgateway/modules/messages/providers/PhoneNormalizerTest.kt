package me.capcom.smsgateway.modules.messages.providers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneNormalizerTest {

    private fun normalizer(countryCode: String = "RU") =
        PhoneNormalizer(fakeContext(), fakeEncryptionService()) { countryCode }

    @Test
    fun e164Passthrough() = runTest {
        assertEquals(
            "+79001234567",
            normalizer().normalize("+79001234567", isEncrypted = false, skipValidation = false),
        )
    }

    @Test
    fun nationalNumberFormatsToE164() = runTest {
        assertEquals(
            "+79001234567",
            normalizer().normalize("89001234567", isEncrypted = false, skipValidation = false),
        )
    }

    @Test
    fun countryCodeProviderSeamIsUsed() = runTest {
        assertEquals(
            "+12312322334",
            normalizer("US").normalize("+1 231-232-2334", isEncrypted = false, skipValidation = false),
        )
    }

    @Test
    fun skipValidationKeepsDigitsAndSymbols() = runTest {
        assertEquals(
            "+7*123#",
            normalizer().normalize("+7*123#abc", isEncrypted = false, skipValidation = true),
        )
    }

    @Test
    fun invalidNumberThrows() = runTest {
        runCatching {
            normalizer().normalize("123456789", isEncrypted = false, skipValidation = false)
        }.exceptionOrNull()?.let { return@runTest }

        throw AssertionError("expected invalid number to throw")
    }
}
