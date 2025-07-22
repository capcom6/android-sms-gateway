package me.capcom.smsgateway.modules.gateway.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import me.capcom.smsgateway.helpers.SSEManager
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.notifications.NotificationsService
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class SSEForegroundService : Service() {
    private val settings: GatewaySettings by inject()
    private val eventBus = get<EventBus>()

    private val notificationsSvc: NotificationsService by inject()

    private val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.javaClass.name)
        }
    }
    private val wifiLock: WifiManager.WifiLock by lazy {
        (getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            this.javaClass.name
        )
    }

    private val sseManager by lazy {
        SSEManager(
            "${settings.serverUrl}/events",
            requireNotNull(
                settings.registrationInfo?.token
            ) { "Authentication token is required for SSE connection" }
        )
            .apply {
                onEvent = { event, data ->
                    Log.d("SSEForegroundService", "$event: $data")
                }
            }
    }

    override fun onCreate() {
        super.onCreate()

        if (!wakeLock.isHeld) {
            wakeLock.acquire()
        }
        if (!wifiLock.isHeld) {
            wifiLock.acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationsSvc.makeNotification(
            this,
            NotificationsService.NOTIFICATION_ID_PING_SERVICE,
            "Listening to the server events..."
        )

        startForeground(NotificationsService.NOTIFICATION_ID_PING_SERVICE, notification)

        sseManager.connect()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        sseManager.disconnect()
        if (wifiLock.isHeld) {
            wifiLock.release()
        }
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        stopForeground(true)

        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SSEForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SSEForegroundService::class.java))
        }
    }
}