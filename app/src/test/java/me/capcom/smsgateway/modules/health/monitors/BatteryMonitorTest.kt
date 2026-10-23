package me.capcom.smsgateway.modules.health.monitors

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryMonitorTest {
    @Test
    fun `charging flags preserve existing AC and USB values`() {
        assertEquals(0L, encodeChargingFlags(BatteryManager.BATTERY_STATUS_FULL, 0))
        assertEquals(1L, encodeChargingFlags(BatteryManager.BATTERY_STATUS_CHARGING, 0))
        assertEquals(2L, encodeChargingFlags(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals(3L, encodeChargingFlags(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals(4L, encodeChargingFlags(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals(5L, encodeChargingFlags(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB))
    }

    @Test
    fun `wireless source stays visible when charging pauses at full`() {
        assertEquals(
            8L,
            encodeChargingFlags(
                BatteryManager.BATTERY_STATUS_FULL,
                BatteryManager.BATTERY_PLUGGED_WIRELESS
            )
        )
    }

    @Test
    fun `wireless source and active charging have distinct flags`() {
        assertEquals(
            9L,
            encodeChargingFlags(
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_PLUGGED_WIRELESS
            )
        )
    }
}
