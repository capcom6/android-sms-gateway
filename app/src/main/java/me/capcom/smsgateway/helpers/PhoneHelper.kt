package me.capcom.smsgateway.helpers

object PhoneHelper {
    fun filterPhoneNumber(phoneNumber: String): String? {
        val number = phoneNumber.replace("[^0-9]".toRegex(), "")
        return when {
            number.length == 10 && number.first() == '9' -> "+7$number"
            number.length == 11 -> when (number.substring(0, 2)) {
                "89" -> "+7${number.substring(1)}"
                "79" -> "+$number"
                else -> null
            }
            else -> null
        }
    }
}