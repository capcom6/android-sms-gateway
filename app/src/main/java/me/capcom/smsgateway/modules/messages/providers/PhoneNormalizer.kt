package me.capcom.smsgateway.modules.messages.providers

import android.content.Context
import android.telephony.TelephonyManager
import me.capcom.smsgateway.helpers.PhoneHelper
import me.capcom.smsgateway.modules.encryption.EncryptionService

class PhoneNormalizer(
    private val context: Context,
    private val encryptionService: EncryptionService,
    private val countryCodeProvider: () -> String? = {
        (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkCountryIso
    },
) {
    suspend fun normalize(source: String, isEncrypted: Boolean, skipValidation: Boolean): String {
        val phone = if (isEncrypted) encryptionService.decrypt(source) else source
        return if (skipValidation) {
            phone.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        } else {
            PhoneHelper.filterPhoneNumber(phone, countryCodeProvider() ?: "RU")
        }
    }
}
