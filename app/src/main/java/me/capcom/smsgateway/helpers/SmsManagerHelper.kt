package me.capcom.smsgateway.helpers

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/**
 * Shared SmsManager resolution for the API 26/31 forks. Used by both the SMS
 * send providers (which resolve slot -> subscription in BaseSmsSendProvider)
 * and MmsSender (which resolves the subscription itself).
 */
object SmsManagerHelper {
    /**
     * SmsManager for [subscriptionId]; null or negative values resolve to the
     * device default (the contract MmsSender already had).
     */
    @Suppress("DEPRECATION")
    fun forSubscription(context: Context, subscriptionId: Int?): SmsManager {
        if (subscriptionId == null || subscriptionId < 0) {
            return default(context)
        }

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 ->
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)

            else -> SmsManager.getDefault()
        }
    }

    @Suppress("DEPRECATION")
    private fun default(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
}
