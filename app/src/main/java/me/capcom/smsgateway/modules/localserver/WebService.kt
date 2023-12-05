package me.capcom.smsgateway.modules.localserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import me.capcom.smsgateway.R
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.domain.MessageState
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.localserver.domain.PostMessageRequest
import me.capcom.smsgateway.modules.localserver.domain.PostMessageResponse
import me.capcom.smsgateway.modules.messages.MessagesService
import org.koin.android.ext.android.inject
import kotlin.concurrent.thread

class WebService : Service() {

    private val settingsHelper by lazy { SettingsHelper(this) }
    private val messagesService: MessagesService by inject()

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
            routing {
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
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("message" to "message is empty")
                                )
                            }
                            if (request.phoneNumbers.isNullOrEmpty()) {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("message" to "phoneNumbers is empty")
                                )
                            }
                            if (request.simNumber != null && request.simNumber < 1) {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("message" to "simNumber must be >= 1")
                                )
                            }

                            val message = try {
                                messagesService.sendMessage(
                                    request.id,
                                    request.message,
                                    request.phoneNumbers,
                                    Message.Source.Local,
                                    request.simNumber?.let { it - 1 }
                                )
                            } catch (e: IllegalArgumentException) {
                                return@post call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("message" to e.message)
                                )
                            } catch (e: Throwable) {
                                return@post call.respond(HttpStatusCode.InternalServerError, mapOf("message" to e.message))
                            }

                            call.respond(
                                HttpStatusCode.Created,
                                PostMessageResponse(
                                    id = message.message.id,
                                    state = message.message.state.toApiState(),
                                    recipients = message.recipients.map {
                                        PostMessageResponse.Recipient(
                                            it.phoneNumber,
                                            it.state.toApiState(),
                                            it.error
                                        )
                                    }
                                )
                            )
                        }
                        get("{id}") {
                            val id = call.parameters["id"]
                                ?: return@get call.respond(HttpStatusCode.BadRequest)

                            val message = try {
                                messagesService.getMessage(id)
                                    ?: return@get call.respond(HttpStatusCode.NotFound)
                            } catch (e: Throwable) {
                                return@get call.respond(HttpStatusCode.InternalServerError, mapOf("message" to e.message))
                            }

                            call.respond(PostMessageResponse(
                                message.message.id,
                                message.message.state.toApiState(),
                                message.recipients.map {
                                    PostMessageResponse.Recipient(
                                        it.phoneNumber,
                                        it.state.toApiState(),
                                        it.error
                                    )
                                }
                            ))
                        }
                    }
                }

            }
        }
    }

    private fun Message.State.toApiState(): MessageState = when (this) {
        Message.State.Pending -> MessageState.Pending
        Message.State.Processed -> MessageState.Processed
        Message.State.Sent -> MessageState.Sent
        Message.State.Delivered -> MessageState.Delivered
        Message.State.Failed -> MessageState.Failed
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = getString(R.string.sms_gateway)
            val descriptionText = getString(R.string.local_sms_gateway_notifications)
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
            .setContentText(
                getString(
                    R.string.sms_gateway_is_running_on_port,
                    settingsHelper.serverPort
                )
            )
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