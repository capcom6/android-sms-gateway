package me.capcom.smsgateway.modules.localserver.domain.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

internal class PostMessageRequestTest {

    private fun request(phoneNumbers: List<String>): PostMessageRequest =
        PostMessageRequest(
            id = null,
            message = "test",
            phoneNumbers = phoneNumbers,
            simNumber = null,
            withDeliveryReport = null,
            isEncrypted = null,
            _ttl = null,
            _validUntil = null,
        )

    @Test
    fun duplicatePhoneNumbersThrows() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            request(listOf("0123456789", "0123456789")).validate()
        }

        assertEquals("phone numbers must be unique", e.message)
    }

    @Test
    fun duplicatePhoneNumbersNonAdjacentThrows() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            request(listOf("0123456789", "0987654321", "0123456789")).validate()
        }

        assertEquals("phone numbers must be unique", e.message)
    }

    @Test
    fun distinctPhoneNumbersPass() {
        val request = request(listOf("0123456789", "0987654321", "+79111111111"))

        assertSame(request, request.validate())
    }

    @Test
    fun singlePhoneNumberPasses() {
        val request = request(listOf("0123456789"))

        assertSame(request, request.validate())
    }

    @Test
    fun emptyPhoneNumbersThrows() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            request(emptyList()).validate()
        }

        assertEquals("Empty phone numbers list", e.message)
    }
}
