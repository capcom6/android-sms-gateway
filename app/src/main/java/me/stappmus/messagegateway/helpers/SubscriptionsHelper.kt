package me.stappmus.messagegateway.helpers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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

    @SuppressLint("MissingPermission")
    fun selectAvailableSimSlots(context: Context): Set<Int>? {
        if (!hasPhoneStatePermission(context)) {
            return null
        }

        val subscriptionManager = getSubscriptionsManager(context) ?: return null
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 -> subscriptionManager.activeSubscriptionInfoList.map { it.simSlotIndex }
                .toSet()
            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    fun getSubscriptionId(context: Context, simSlotIndex: Int): Int? {
        if (!hasPhoneStatePermission(context)) {
            return null
        }

        val subscriptionManager = getSubscriptionsManager(context) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            subscriptionManager.activeSubscriptionInfoList.find {
                it.simSlotIndex == simSlotIndex
            }?.subscriptionId
        } else {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun getSimSlotIndex(context: Context, subscriptionId: Int): Int? {
        if (!hasPhoneStatePermission(context)) {
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

    fun hasPhoneStatePermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("InlinedApi")
    fun extractSubscriptionId(context: Context, intent: Intent): Int? {
        return when {
            intent.extras?.containsKey(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX) == true -> intent.extras?.getInt(
                SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX
            )

            intent.extras?.containsKey("subscription") == true -> intent.extras?.getInt("subscription")
            intent.extras?.containsKey(SubscriptionManager.EXTRA_SLOT_INDEX) == true -> intent.extras?.getInt(
                SubscriptionManager.EXTRA_SLOT_INDEX
            )?.let { getSubscriptionId(context, it) }

            else -> null
        }
    }
}