package me.capcom.smsgateway.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.capcom.smsgateway.R
import kotlin.concurrent.thread

class WebService : Service() {

//    private val server by lazy { WebServer() }

    private val server by lazy {
        embeddedServer(Netty, PORT, watchPaths = emptyList()) {
//            install(CallLogging)
            routing {
                get("/") {
                    call.respondText(
                        text = "Hello! You are here in ${Build.MODEL}",
                        contentType = ContentType.Text.Plain
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = "SMS-шлюз"
            val descriptionText = "Уведомления о работе шлюза"
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }

        server.start()
//        server.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getText(R.string.notification_title))
            .setContentText(getText(R.string.notification_message))
            .setSmallIcon(R.drawable.ic_sms)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onDestroy() {
        thread { server.stop() }

        stopForeground(true)

        super.onDestroy()
    }

    companion object {        const val NOTIFICATION_CHANNEL_ID = "WEBSERVICE"
        const val NOTIFICATION_ID = 1

        private const val PORT = 53954

        fun start(context: Context) {
            val intent = Intent(context, WebService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebService::class.java))
        }
    }
}