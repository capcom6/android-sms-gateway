package me.capcom.smsgateway.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat

object SubscriptionsHelper {
    @Suppress("DEPRECATION")
    fun getSubscriptionsManager(context: Context): SubscriptionManager? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 -> null
        Build.VERSION.SDK_INT < 31 -> SubscriptionManager.from(context)
        else -> context.getSystemService(SubscriptionManager::class.java)
    }

    fun getSimSlotIndex(context: Context, subscriptionId: Int): Int? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val subscriptionManager = getSubscriptionsManager(context) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            subscriptionManager.activeSubscriptionInfoList.find {
                it.subscriptionId == subscriptionId
            }?.simSlotIndex
        } else {
            null
        }
    }
}