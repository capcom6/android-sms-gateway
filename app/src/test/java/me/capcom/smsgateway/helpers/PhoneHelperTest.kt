package me.capcom.smsgateway.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

internal class PhoneHelperTest {

    @Test
    fun filterPhoneNumber() {
        val tests = mapOf(
            "123456789" to null,
            "9001234567" to "+79001234567",
            "+79001234567" to "+79001234567",
            "79001234567" to "+79001234567",
            "7900123456" to null,
            "89001234567" to "+79001234567",
        )

        tests.forEach { (t, u) ->
            assertEquals(u, PhoneHelper.filterPhoneNumber(t))
        }
    }
}