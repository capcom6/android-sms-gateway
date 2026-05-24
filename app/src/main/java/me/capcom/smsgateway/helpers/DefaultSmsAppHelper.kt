package me.capcom.smsgateway.helpers

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.annotation.RequiresApi

/**
 * Helpers for checking and requesting the default SMS app role.
 *
 * Becoming the default SMS app is needed for:
 *  - Receiving `WAP_PUSH_DELIVER_ACTION` so we can actively download MMS PDUs
 *  - Reliable MMS sending on some carriers
 */
object DefaultSmsAppHelper {

    fun isDefault(context: Context): Boolean {
        // Prefer RoleManager on Android 10+. `Telephony.Sms.getDefaultSmsPackage`
        // reads the legacy `sms_default_application` secure setting, which is
        // not always populated — notably when the role is granted via the
        // role API (e.g. `cmd role add-role-holder`) without going through
        // the RoleManager UI, the role is held but the legacy setting stays
        // null, giving the wrong answer.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            }
        }
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    /**
     * Request the default SMS app role. On Android 10+ (Q) uses the RoleManager
     * API; on older platforms falls back to the legacy
     * [Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT] intent.
     */
    fun requestDefault(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestViaRoleManager(activity, requestCode)
            return
        }
        @Suppress("DEPRECATION")
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
        }
        activity.startActivityForResult(intent, requestCode)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestViaRoleManager(activity: Activity, requestCode: Int) {
        val roleManager = activity.getSystemService(RoleManager::class.java) ?: return
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) return
        if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) return
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        activity.startActivityForResult(intent, requestCode)
    }
}
