package me.capcom.smsgateway.extensions

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

fun Context.setComponentEnabled(
    component: Class<*>,
    enabled: Boolean,
) {
    packageManager.setComponentEnabledSetting(
        ComponentName(this, component),
        if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        },
        PackageManager.DONT_KILL_APP
    )
}