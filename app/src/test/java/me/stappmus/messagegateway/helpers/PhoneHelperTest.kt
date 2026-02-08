package me.stappmus.messagegateway.helpers

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
            "+1 231-232-2334" to "+12312322334", // United States
            "+44 20 1234 5678" to null, // United Kingdom fixed line
            "+44 7911 123456" to "+447911123456", // United Kingdom mobile
            "+33 1 23 45 67 89" to null, // France fixed line
            "+49 30 12345678" to null, // Germany fixed line
            "+49 171 1234567" to "+491711234567", // Germany mobile
            "+61 2 1234 5678" to null, // Australia fixed line
            "+61 4 1234 5678" to "+61412345678", // Australia mobile
            "+61493563919" to "+61493563919", // Australia mobile from https://github.com/capcom6/android-sms-gateway/issues/28
            "+81 3 1234 5678" to null, // Japan fixed line
            "+1 416-555-1234" to "+14165551234", // Canada
            "+91 98765 43210" to "+919876543210", // India
            "+212724364434" to "+212724364434",
        )

        tests.forEach { (t, u) ->
            assertEquals(
                u,
                try {
                    PhoneHelper.filterPhoneNumber(t, "RU")
                } catch (e: Exception) {
                    null
                }
            )
        }
    }
}