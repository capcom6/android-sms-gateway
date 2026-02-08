package me.stappmus.messagegateway.helpers

import com.google.i18n.phonenumbers.PhoneNumberUtil

object PhoneHelper {
    fun filterPhoneNumber(phoneNumber: String, countryCode: String): String {
        val phoneUtil = PhoneNumberUtil.getInstance()
        val number = phoneUtil.parse(phoneNumber, countryCode.uppercase())
        if (!phoneUtil.isValidNumber(number)) {
            throw RuntimeException("Invalid phone number")
        }
        if (phoneUtil.getNumberType(number) != PhoneNumberUtil.PhoneNumberType.MOBILE
            && phoneUtil.getNumberType(number) != PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE
        ) {
            throw RuntimeException("Invalid phone number type")
        }
        return phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
    }
}