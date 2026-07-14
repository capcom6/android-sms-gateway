package me.capcom.smsgateway.helpers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat
import me.capcom.smsgateway.modules.localserver.domain.SimCard

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

    @SuppressLint("MissingPermission")
    fun getPhoneNumber(context: Context, simSlotIndex: Int): String? {
        if (!hasPhoneStatePermission(context)) {
            return null
        }

        val subscriptionManager = getSubscriptionsManager(context) ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            subscriptionManager.activeSubscriptionInfoList?.find {
                it.simSlotIndex == simSlotIndex
            }?.let { resolvePhoneNumber(subscriptionManager, it) }
        } else {
            null
        }
    }

    /**
     * Retrieves information about all active SIM cards in the device.
     *
     * Returns a list of SimCard objects for each active subscription.
     * Phone numbers will be null if READ_PHONE_STATE permission is not granted.
     * Returns empty list on API levels below LOLLIPOP_MR1 (22).
     *
     * @param context Android context for accessing system services
     * @return List of active SIM cards, empty if none available or API level too low
     */
    @SuppressLint("MissingPermission")
    fun getActiveSimCards(context: Context): List<SimCard> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return emptyList()
        }

        val subscriptionManager = getSubscriptionsManager(context) ?: return emptyList()

        val activeSubscriptions = try {
            subscriptionManager.activeSubscriptionInfoList ?: return emptyList()
        } catch (_: SecurityException) {
            return emptyList()
        }

        val hasPermission = hasPhoneStatePermission(context)

        return activeSubscriptions.map { info ->
            SimCard(
                slotIndex = info.simSlotIndex,
                simNumber = info.simSlotIndex + 1,
                phoneNumber = if (hasPermission) {
                    resolvePhoneNumber(subscriptionManager, info)
                } else {
                    null
                },
                carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() },
                iccid = info.iccId?.takeIf { it.isNotBlank() },
            )
        }
    }

    /**
     * Resolves the phone number for a subscription, preferring the modern
     * SubscriptionManager.getPhoneNumber() on Android 13+ (which can source the
     * number from the carrier/IMS, not just the SIM) and falling back to the
     * deprecated SubscriptionInfo.number on older devices. May still be null if
     * the carrier never provisioned the number.
     */
    @SuppressLint("MissingPermission")
    private fun resolvePhoneNumber(
        subscriptionManager: SubscriptionManager,
        info: android.telephony.SubscriptionInfo,
    ): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { subscriptionManager.getPhoneNumber(info.subscriptionId) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        } ?: info.number?.takeIf { it.isNotBlank() }
    }
}