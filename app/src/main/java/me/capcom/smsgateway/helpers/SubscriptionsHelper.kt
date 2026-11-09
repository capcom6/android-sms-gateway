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

/**
 * How the SMS provider's `sub_id` column should be read.
 *
 * - [SUBSCRIPTION_ID]: stock Android, the column holds the subscription id.
 * - [SLOT_INDEX]: some OEM builds (Huawei EMUI observed) store the SIM slot index.
 * - [UNKNOWN]: not learned yet.
 */
enum class ProviderSubIdMode { UNKNOWN, SUBSCRIPTION_ID, SLOT_INDEX }

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
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 -> subscriptionManager.activeSubscriptionInfoList
                ?.map { it.simSlotIndex }
                ?.toSet()

            else -> null
        }
    }

    /** Subscription id of the SIM in the given slot (0-based), by slot index. */
    fun getSubscriptionId(context: Context, simSlotIndex: Int): Int? =
        getActiveSubscriptions(context)?.let { findSubscriptionId(it, simSlotIndex) }

    /** Slot index (0-based) of the active subscription, by subscription id. */
    fun getSimSlotIndex(context: Context, subscriptionId: Int): Int? =
        getActiveSubscriptions(context)?.find { it.first == subscriptionId }?.second

    /**
     * Normalizes the `sub_id` read from the SMS provider into a real subscription id.
     *
     * Some OEM builds (e.g. Huawei EMUI) store the SIM slot index in `sub_id` instead of the
     * subscription id. Subscription ids may be non-contiguous (1 and 3 for slots 0 and 1), so
     * a slot index must be mapped through the active subscriptions.
     *
     * @param value the raw `sub_id` from the provider
     * @param active active subscriptions as (subscriptionId, simSlotIndex) pairs
     * @return the value itself if it is an active subscription id; the subscription id of the
     * slot if it is a valid slot index; otherwise the value unchanged
     */
    fun normalizeProviderSubId(value: Int, active: List<Pair<Int, Int>>): Int {
        if (active.any { it.first == value }) {
            return value
        }
        if (value in 0 until active.size) {
            active.find { it.second == value }?.let { return it.first }
        }
        return value
    }

    /**
     * Resolves a raw provider `sub_id` into a subscription id.
     *
     * With a slot index and subscription ids that overlap (EMUI: ids 1 and 3, slots 0 and 1,
     * so a value of 1 is both "subscription 1" and "slot 1"), the value alone is ambiguous.
     * It is therefore resolved by [mode] (learned from a broadcast twin) or, while [mode] is
     * [ProviderSubIdMode.UNKNOWN], by the vendor hint [vendorUsesSlotIndex].
     * With neither, the value is kept when it is an active subscription id (stock Android),
     * otherwise mapped through the slot when it is a valid slot index.
     *
     * @param active active subscriptions as (subscriptionId, simSlotIndex) pairs
     */
    fun resolveProviderSubId(
        value: Int,
        active: List<Pair<Int, Int>>,
        mode: ProviderSubIdMode,
        vendorUsesSlotIndex: Boolean,
    ): Int {
        val effective = when {
            mode != ProviderSubIdMode.UNKNOWN -> mode
            vendorUsesSlotIndex -> ProviderSubIdMode.SLOT_INDEX
            else -> ProviderSubIdMode.UNKNOWN
        }
        return when (effective) {
            ProviderSubIdMode.SUBSCRIPTION_ID -> value
            ProviderSubIdMode.SLOT_INDEX -> active.find { it.second == value }?.first ?: value
            ProviderSubIdMode.UNKNOWN -> normalizeProviderSubId(value, active)
        }
    }

    /**
     * Learns how the provider writes `sub_id` from one message seen on both paths: the
     * broadcast (which carries the real subscription id) and the provider row.
     *
     * @return null when the pair is uninformative (subscription id equals the slot index, the
     * subscription is not active, or the value matches neither reading)
     */
    fun inferProviderSubIdMode(
        providerValue: Int,
        broadcastSubscriptionId: Int,
        active: List<Pair<Int, Int>>,
    ): ProviderSubIdMode? {
        val slot = active.find { it.first == broadcastSubscriptionId }?.second ?: return null
        if (slot == broadcastSubscriptionId) {
            return null
        }
        return when (providerValue) {
            broadcastSubscriptionId -> ProviderSubIdMode.SUBSCRIPTION_ID
            slot -> ProviderSubIdMode.SLOT_INDEX
            else -> null
        }
    }

    /** Vendors whose SMS provider is known to store the slot index in `sub_id`. */
    fun providerUsesSlotIndex(manufacturer: String?): Boolean {
        return when (manufacturer?.trim()?.lowercase()) {
            "huawei", "honor" -> true
            else -> false
        }
    }

    /** Subscription id of the SIM in [slotIndex], looked up by slot (never by list position). */
    fun findSubscriptionId(active: List<Pair<Int, Int>>, slotIndex: Int): Int? =
        active.find { it.second == slotIndex }?.first

    /** API `simNumber` (1-based) to slot index (0-based); null for absent or invalid values. */
    fun slotIndexForSimNumber(simNumber: Int?): Int? =
        simNumber?.takeIf { it >= 1 }?.let { it - 1 }

    fun simNumberForSlotIndex(slotIndex: Int?): Int? = slotIndex?.let { it + 1 }

    fun simNumberForSubscriptionId(active: List<Pair<Int, Int>>, subscriptionId: Int?): Int? =
        simNumberForSlotIndex(
            subscriptionId?.let { id -> active.find { it.first == id }?.second }
        )

    @SuppressLint("MissingPermission")
    fun getActiveSubscriptions(context: Context): List<Pair<Int, Int>>? {
        if (!hasPhoneStatePermission(context)) {
            return null
        }

        val subscriptionManager = getSubscriptionsManager(context) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }
        return try {
            subscriptionManager.activeSubscriptionInfoList
                ?.map { it.subscriptionId to it.simSlotIndex }
        } catch (_: SecurityException) {
            null
        }
    }

    fun resolveProviderSubId(
        context: Context,
        value: Int,
        mode: ProviderSubIdMode,
    ): Int {
        val active = getActiveSubscriptions(context) ?: return value
        return resolveProviderSubId(
            value,
            active,
            mode,
            providerUsesSlotIndex(Build.MANUFACTURER)
        )
    }

    fun inferProviderSubIdMode(
        context: Context,
        providerValue: Int,
        broadcastSubscriptionId: Int,
    ): ProviderSubIdMode? {
        val active = getActiveSubscriptions(context) ?: return null
        return inferProviderSubIdMode(providerValue, broadcastSubscriptionId, active)
    }

    private fun hasPhoneStatePermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("InlinedApi")
    fun extractSubscriptionId(context: Context, intent: Intent): Int? {
        val extras = intent.extras ?: return null
        fun intOrNull(key: String): Int? =
            if (extras.containsKey(key)) extras.getInt(key) else null

        return pickSubscriptionId(
            intOrNull(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX),
            intOrNull("subscription"),
            intOrNull(SubscriptionManager.EXTRA_SLOT_INDEX),
        ) { slot -> getSubscriptionId(context, slot) }
    }

    /**
     * Picks the subscription id from the intent extras, in order: the subscription index
     * extra, the legacy `subscription` extra, then the slot index extra mapped through
     * [slotToSubscriptionId]. A negative value (the platform's "invalid subscription")
     * is skipped so a usable later extra can still decide.
     */
    fun pickSubscriptionId(
        subscriptionIndex: Int?,
        subscription: Int?,
        slotIndex: Int?,
        slotToSubscriptionId: (Int) -> Int?,
    ): Int? {
        subscriptionIndex?.takeIf { it >= 0 }?.let { return it }
        subscription?.takeIf { it >= 0 }?.let { return it }
        return slotIndex?.takeIf { it >= 0 }?.let(slotToSubscriptionId)
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
     * Phone numbers will be null if READ_PHONE_STATE permission is not granted, or unavailable
     * on Android 13+ without READ_PHONE_NUMBERS permission.
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