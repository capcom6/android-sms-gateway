package me.capcom.smsgateway.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Stub service required for the default SMS app role. Phone calls surface
 * "respond via message" canned replies through this service; we accept the
 * intent and no-op because a gateway device does not expose a human UI.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
}
