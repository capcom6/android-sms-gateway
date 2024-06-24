package me.capcom.smsgateway.modules.ping.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import me.capcom.smsgateway.R
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.notifications.NotificationsService
import me.capcom.smsgateway.modules.ping.PingSettings
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import kotlin.concurrent.thread

class PingForegroundService : Service() {
    private val settings: PingSettings by inject()

    private val notificationsSvc: NotificationsService by inject()

    private val gatewaySvc: GatewayService by inject()

    private val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.javaClass.name)
        }
    }

    @Volatile
    private var stopRequested = false

    private val workingThread = thread(start = false) {
        val interval = settings.intervalSeconds ?: return@thread
        while (!stopRequested) {
            Thread.sleep(interval * 1000L)

            Log.d(this.javaClass.name, "Sending ping")
            gatewaySvc.ping(get())
        }
    }

    override fun onCreate() {
        super.onCreate()

        wakeLock.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationsSvc.makeNotification(
            this,
            getString(R.string.ping_service_is_active)
        )

        startForeground(NotificationsService.NOTIFICATION_ID_PING_SERVICE, notification)

        workingThread.start()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onDestroy() {
        stopRequested = true
        workingThread.join()
        wakeLock.release()
        stopForeground(true)

        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PingForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PingForegroundService::class.java))
        }
    }
}