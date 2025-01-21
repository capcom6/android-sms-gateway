package me.capcom.smsgateway.modules.health.monitors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import me.capcom.smsgateway.modules.health.domain.CheckResult
import me.capcom.smsgateway.modules.health.domain.Status

class BatteryMonitor(
    private val context: Context
) {
    fun healthCheck(): Map<String, CheckResult> {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            .let { ifilter ->
                context.registerReceiver(null, ifilter)
            }

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL

        // How are we charging?
        val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val usbCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        val acCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_AC

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
                when {
                    acCharge -> 2L
                    usbCharge -> 4L
                    isCharging -> 1L
                    else -> 0L
                },
                "flags",
                "Is the phone charging?"
            ),
        )
    }
}