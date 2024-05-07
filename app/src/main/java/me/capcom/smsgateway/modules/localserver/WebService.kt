package me.capcom.smsgateway.modules.localserver

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
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
import io.ktor.server.plugins.statuspages.StatusPages
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
import me.capcom.smsgateway.modules.localserver.domain.Device
import me.capcom.smsgateway.modules.localserver.domain.PostMessageRequest
import me.capcom.smsgateway.modules.localserver.domain.PostMessageResponse
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.messages.data.MessageSource
import me.capcom.smsgateway.modules.messages.data.SendRequest
import me.capcom.smsgateway.modules.notifications.NotificationsService
import org.koin.android.ext.android.inject
import java.util.Date
import java.util.TimeZone
import kotlin.concurrent.thread

class WebService : Service() {

    private val settingsHelper: SettingsHelper by inject()
    private val messagesService: MessagesService by inject()
    private val notificationsService: NotificationsService by inject()

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
            install(ContentNegotiation) {
                gson {
                    if (me.capcom.smsgateway.BuildConfig.DEBUG) {
                        setPrettyPrinting()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        this.setDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
                        )
                    } else {
                        //get device timezone
                        val timeZone = TimeZone.getDefault()
                        this.setDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSS" + when (timeZone.rawOffset) {
                                0 -> "Z"
                                else -> "+" + (timeZone.rawOffset / 3600000).toString().padStart(
                                    2,
                                    '0'
                                ) + ":" + ((timeZone.rawOffset % 3600000) / 60000).toString()
                                    .padStart(2, '0')
                            }
                        )
                    }
                }
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(
                        when (cause) {
                            is IllegalArgumentException -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.InternalServerError
                        },
                        mapOf("message" to cause.message)
                    )
                }
            }
            routing {
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
                    route("/device") {
                        get {
                            val firstInstallTime = packageManager.getPackageInfo(
                                packageName,
                                0
                            ).firstInstallTime
                            val deviceName = "${Build.MANUFACTURER}/${Build.PRODUCT}"
                            val deviceId =
                                deviceName.hashCode().toULong()
                                    .toString(16).padStart(16, '0') + firstInstallTime.toULong()
                                    .toString(16).padStart(16, '0')
                            val device = Device(
                                deviceId,
                                deviceName,
                                Date(firstInstallTime),
                                Date(),
                                Date()
                            )

                            call.respond(listOf(device))
                        }
                    }
                    route("/message") {
                        post {
                            val request = call.receive<PostMessageRequest>()
                            if (request.message.isEmpty()) {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("message" to "message is empty")
                                )
                            }
                            if (request.phoneNumbers.isEmpty()) {
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
                            val skipPhoneValidation =
                                call.request.queryParameters["skipPhoneValidation"]
                                    ?.toBooleanStrict() ?: false

                            val sendRequest = SendRequest(
                                MessageSource.Local,
                                me.capcom.smsgateway.modules.messages.data.Message(
                                    request.id ?: NanoIdUtils.randomNanoId(),
                                    request.message,
                                    request.phoneNumbers,
                                    request.isEncrypted ?: false
                                ),
                                me.capcom.smsgateway.modules.messages.data.SendParams(
                                    request.withDeliveryReport ?: true,
                                    skipPhoneValidation = skipPhoneValidation,
                                    simNumber = request.simNumber,
                                    validUntil = request.validUntil,
                                )
                            )
                            messagesService.enqueueMessage(sendRequest)

                            val messageId = sendRequest.message.id

                            call.respond(
                                HttpStatusCode.Accepted,
                                PostMessageResponse(
                                    id = messageId,
                                    state = MessageState.Pending,
                                    recipients = request.phoneNumbers.map {
                                        PostMessageResponse.Recipient(
                                            it,
                                            MessageState.Pending,
                                            null
                                        )
                                    },
                                    isEncrypted = request.isEncrypted ?: false
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
                                return@get call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("message" to e.message)
                                )
                            }

                            call.respond(
                                PostMessageResponse(
                                    message.message.id,
                                    message.message.state.toApiState(),
                                    message.recipients.map {
                                        PostMessageResponse.Recipient(
                                            it.phoneNumber,
                                            it.state.toApiState(),
                                            it.error
                                        )
                                    },
                                    message.message.isEncrypted
                                )
                            )
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

        server.start()
        wakeLock.acquire()

        status.postValue(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationsService.makeNotification(
            this,
            getString(
                R.string.sms_gateway_is_running_on_port,
                settingsHelper.serverPort
            )
        )

        startForeground(NotificationsService.NOTIFICATION_ID_LOCAL_SERVICE, notification)

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