package me.capcom.smsgateway.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.capcom.smsgateway.App
import me.capcom.smsgateway.R
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.domain.PostMessageRequest
import me.capcom.smsgateway.domain.PostMessageResponse
import me.capcom.smsgateway.helpers.PhoneHelper
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.receivers.EventsReceiver
import kotlin.concurrent.thread

class WebService : Service() {

    private val settingsHelper by lazy { SettingsHelper(this) }

    private val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.javaClass.name)
        }
    }

    private val server by lazy {
        embeddedServer(Netty, settingsHelper.serverPort, watchPaths = emptyList()) {
            install(Authentication) {
                basic("auth-basic") {
                    realm = "Access to SMS Gateway"
                    validate { credentials ->
                        if (credentials.name == "sms" && credentials.password == settingsHelper.serverToken) {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                }
            }
//            install(CallLogging) {
//                this.level = Level.DEBUG
//            }
            routing {
//                install(Compression)
                install(ContentNegotiation) {
                    gson {
                        if (me.capcom.smsgateway.BuildConfig.DEBUG) {
                            setPrettyPrinting()
                        }
                    }
                }
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Authorization)
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Post)
                }
                authenticate("auth-basic") {
                    get("/") {
                        call.respond(mapOf("status" to "ok", "model" to Build.MODEL))
                    }
                    route("/message") {
                        post {
                            val request = call.receive<PostMessageRequest>()
                            if (request.message.isNullOrEmpty()) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "message is empty"))
                            }
                            if (request.phoneNumbers.isNullOrEmpty()) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "phoneNumbers is empty"))
                            }

                            val id = request.id ?: NanoIdUtils.randomNanoId()
                            App.db.messageDao().insert(Message(id, request.message, request.phoneNumbers))

                            try {
                                sendSMS(id, request.message, request.phoneNumbers)
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to e.message))
                            }

                            call.respond(PostMessageResponse(
                                id = id,
                                state = PostMessageResponse.State.Pending
                            ))
                        }
                        get("{id}") {
                            val id = call.parameters["id"]
                                ?: return@get call.respond(HttpStatusCode.BadRequest)

                            val message = App.db.messageDao().get(id)
                                ?: return@get call.respond(HttpStatusCode.NotFound)

                            call.respond(PostMessageResponse(
                                message.id,
                                when (message.state) {
                                    Message.State.Pending -> PostMessageResponse.State.Pending
                                    Message.State.Sent -> PostMessageResponse.State.Sent
                                    Message.State.Delivered -> PostMessageResponse.State.Delivered
                                    Message.State.Failed -> PostMessageResponse.State.Failed
                                }
                            ))
                        }
                    }
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
        wakeLock.acquire()

        status.postValue(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getText(R.string.notification_title))
            .setContentText("Шлюз запущен на порту ${settingsHelper.serverPort}")
            .setSmallIcon(R.drawable.ic_sms)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onDestroy() {
        wakeLock.release()
        thread { server.stop() }

        stopForeground(true)

        status.postValue(false)

        super.onDestroy()
    }

    private fun sendSMS(id: String, message: String, phoneNumbers: List<String>) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

        val sentIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(EventsReceiver.ACTION_SENT, Uri.parse(id), this, EventsReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val deliveredIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(EventsReceiver.ACTION_DELIVERED, Uri.parse(id), this, EventsReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        phoneNumbers.mapNotNull { PhoneHelper.filterPhoneNumber(it) }.forEach {
            smsManager.sendTextMessage(it, null, message, sentIntent, deliveredIntent)
        }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "WEBSERVICE"
        private const val NOTIFICATION_ID = 1

        private val status = MutableLiveData<Boolean>(false)
        val STATUS: LiveData<Boolean> = status

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