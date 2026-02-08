package me.stappmus.messagegateway.modules.ping.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.stappmus.messagegateway.R
import me.stappmus.messagegateway.domain.HealthResponse
import me.stappmus.messagegateway.modules.events.EventBus
import me.stappmus.messagegateway.modules.health.HealthService
import me.stappmus.messagegateway.modules.notifications.NotificationsService
import me.stappmus.messagegateway.modules.ping.PingSettings
import me.stappmus.messagegateway.modules.ping.events.PingEvent
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import kotlin.concurrent.thread

class PingForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val settings: PingSettings by inject()

    private val eventBus = get<EventBus>()

    private val notificationsSvc: NotificationsService by inject()
    private val healthService: HealthService by inject()

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

    @Volatile
    private var stopRequested = false

    private val workingThread = thread(start = false, priority = Thread.MIN_PRIORITY) {
        val interval = settings.intervalSeconds ?: return@thread
        while (!stopRequested) {
            try {
                Log.d(this.javaClass.name, "Sending ping")
                scope.launch {
                    eventBus.emit(PingEvent(HealthResponse(healthService.healthCheck())))
                }

                Thread.sleep(interval * 1000L)
            } catch (_: Throwable) {

            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        wakeLock.acquire()
        wifiLock.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationsSvc.makeNotification(
            this,
            NotificationsService.NOTIFICATION_ID_PING_SERVICE,
            getString(R.string.ping_service_is_active)
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NotificationsService.NOTIFICATION_ID_PING_SERVICE,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationsService.NOTIFICATION_ID_PING_SERVICE, notification)
        }

        if (workingThread.state == Thread.State.NEW) {
            workingThread.start()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onDestroy() {
        stopRequested = true
        scope.cancel()
        workingThread.interrupt()
        workingThread.join()
        wifiLock.release()
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
