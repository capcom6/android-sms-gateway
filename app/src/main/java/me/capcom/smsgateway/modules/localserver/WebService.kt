package me.capcom.smsgateway.modules.localserver

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import io.ktor.http.HttpStatusCode
import io.ktor.http.toHttpDate
import io.ktor.serialization.gson.gson
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.date.GMTDate
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.R
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.health.HealthService
import me.capcom.smsgateway.modules.health.domain.Status
import me.capcom.smsgateway.modules.localserver.domain.Device
import me.capcom.smsgateway.modules.localserver.domain.PostMessageRequest
import me.capcom.smsgateway.modules.localserver.domain.PostMessageResponse
import me.capcom.smsgateway.modules.localserver.routes.LogsRoutes
import me.capcom.smsgateway.modules.localserver.routes.WebhooksRoutes
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.messages.data.SendRequest
import me.capcom.smsgateway.modules.notifications.NotificationsService
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import java.util.Date
import kotlin.concurrent.thread

class WebService : Service() {

    private val settings: LocalServerSettings by inject()
    private val messagesService: MessagesService by inject()
    private val notificationsService: NotificationsService by inject()
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

    private val server by lazy {
        embeddedServer(
            Netty,
            port = port,
            watchPaths = emptyList(),
        ) {
            install(Authentication) {
                basic("auth-basic") {
                    realm = "Access to SMS Gateway"
                    validate { credentials ->
                        when {
                            credentials.name == username
                                    && credentials.password == password -> UserIdPrincipal(
                                credentials.name
                            )

                            else -> null
                        }
                    }
                }
            }
            install(ContentNegotiation) {
                gson {
                    if (me.capcom.smsgateway.BuildConfig.DEBUG) {
                        setPrettyPrinting()
                    }
                    configure()
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
            install(createApplicationPlugin(name = "DateHeader") {
                onCall { call ->
                    call.response.header(
                        "Date",
                        GMTDate(null).toHttpDate()
                    )
                }
            })
            routing {
                get("/health") {
                    val healthResult = healthService.healthCheck()
                    call.respond(
                        when (healthResult.status) {
                            Status.FAIL -> HttpStatusCode.InternalServerError
                            Status.WARN -> HttpStatusCode.OK
                            Status.PASS -> HttpStatusCode.OK
                        },
                        mapOf(
                            "status" to healthResult.status,
                            "version" to BuildConfig.VERSION_NAME,
                            "releaseId" to BuildConfig.VERSION_CODE,
                            "checks" to healthResult.checks
                        )
                    )
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
                                EntitySource.Local,
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
                                    state = ProcessingState.Pending,
                                    recipients = request.phoneNumbers.map {
                                        PostMessageResponse.Recipient(
                                            it,
                                            ProcessingState.Pending,
                                            null
                                        )
                                    },
                                    isEncrypted = request.isEncrypted ?: false,
                                    mapOf(ProcessingState.Pending to Date())
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
                                    message.message.state,
                                    message.recipients.map {
                                        PostMessageResponse.Recipient(
                                            it.phoneNumber,
                                            it.state,
                                            it.error
                                        )
                                    },
                                    message.message.isEncrypted,
                                    message.states.associate {
                                        it.state to Date(it.updatedAt)
                                    }
                                )
                            )
                        }
                    }
                    WebhooksRoutes(get()).let {
                        route("/webhook") {
                            it.register(this)
                        }
                        route("/webhooks") {
                            it.register(this)
                        }
                    }

                    route("/logs") {
                        LogsRoutes(get()).register(this)
                    }
                }
            }
        }
    }

    private val port = settings.port
    private val username = settings.username
    private val password = settings.password

    override fun onCreate() {
        super.onCreate()

        server.start()
        wakeLock.acquire()
        wifiLock.acquire()

        status.postValue(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationsService.makeNotification(
            this,
            NotificationsService.NOTIFICATION_ID_LOCAL_SERVICE,
            getString(
                R.string.sms_gateway_is_running_on_port,
                port
            )
        )

        startForeground(NotificationsService.NOTIFICATION_ID_LOCAL_SERVICE, notification)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onDestroy() {
        wifiLock.release()
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