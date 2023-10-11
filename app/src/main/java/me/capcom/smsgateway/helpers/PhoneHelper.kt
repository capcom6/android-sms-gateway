package me.capcom.smsgateway.helpers

import android.util.Log
import com.google.i18n.phonenumbers.PhoneNumberUtil

object PhoneHelper {
    fun filterPhoneNumber(phoneNumber: String, countryCode: String): String? {
        val phoneUtil = PhoneNumberUtil.getInstance()
        try {
            val number = phoneUtil.parse(phoneNumber, countryCode.uppercase())
            if (!phoneUtil.isValidNumber(number)) {
                return null;
            }
            if (phoneUtil.getNumberType(number) != PhoneNumberUtil.PhoneNumberType.MOBILE
                && phoneUtil.getNumberType(number) != PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE) {
                return null
            }
            return phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (e: Exception) {
            Log.d("PhoneHelper", "filterPhoneNumber: ${e.message}")
            return null
        }
    }
}