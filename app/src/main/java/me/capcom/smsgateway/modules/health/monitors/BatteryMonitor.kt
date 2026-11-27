package me.capcom.smsgateway.modules.health.monitors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import me.capcom.smsgateway.modules.health.domain.CheckResult
import me.capcom.smsgateway.modules.health.domain.Status

internal const val CHARGING_ACTIVE_FLAG = 1L
internal const val CHARGING_AC_FLAG = 2L
internal const val CHARGING_USB_FLAG = 4L
internal const val CHARGING_WIRELESS_FLAG = 8L

internal fun encodeChargingFlags(status: Int, chargePlug: Int): Long {
    var flags = if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
        CHARGING_ACTIVE_FLAG
    } else {
        0L
    }

    if (chargePlug >= 0) {
        if (chargePlug and BatteryManager.BATTERY_PLUGGED_AC != 0) {
            flags += CHARGING_AC_FLAG
        }
        if (chargePlug and BatteryManager.BATTERY_PLUGGED_USB != 0) {
            flags += CHARGING_USB_FLAG
        }
        if (chargePlug and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) {
            flags += CHARGING_WIRELESS_FLAG
        }
    }

    return flags
}

class BatteryMonitor(
    private val context: Context
) {
    fun healthCheck(): Map<String, CheckResult> {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            .let { ifilter ->
                context.registerReceiver(null, ifilter)
            }

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }

        val levelStatus = batteryPct?.let {
            when {
                it < 10 -> Status.FAIL
                it < 25 -> Status.WARN
                else -> Status.PASS
            }
        } ?: Status.PASS

        return mapOf(
            "level" to CheckResult(
                levelStatus,
                batteryPct?.toLong() ?: 0L,
                "percent",
                "Battery level in percent"
            ),
            "charging" to CheckResult(
                Status.PASS,
                encodeChargingFlags(status, chargePlug),
                "flags",
                "Charging flags: active=1, AC=2, USB=4, wireless=8"
            ),
        )
    }
}