package me.stappmus.messagegateway.modules.localserver

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.date.GMTDate
import me.stappmus.messagegateway.R
import me.stappmus.messagegateway.domain.HealthResponse
import me.stappmus.messagegateway.extensions.configure
import me.stappmus.messagegateway.modules.health.HealthService
import me.stappmus.messagegateway.modules.health.domain.Status
import me.stappmus.messagegateway.modules.localserver.domain.Device
import me.stappmus.messagegateway.modules.localserver.routes.AuthRoutes
import me.stappmus.messagegateway.modules.localserver.routes.DocsRoutes
import me.stappmus.messagegateway.modules.localserver.routes.LogsRoutes
import me.stappmus.messagegateway.modules.localserver.routes.MediaRoutes
import me.stappmus.messagegateway.modules.localserver.routes.MessagesRoutes
import me.stappmus.messagegateway.modules.localserver.routes.WebhooksRoutes
import me.stappmus.messagegateway.modules.notifications.NotificationsService
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import java.util.Date
import kotlin.concurrent.thread

class WebService : Service() {

    private val settings: LocalServerSettings by inject()
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
                    if (me.stappmus.messagegateway.BuildConfig.DEBUG) {
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
                            is BadRequestException -> HttpStatusCode.BadRequest
                            is NotFoundException -> HttpStatusCode.NotFound
                            else -> HttpStatusCode.InternalServerError
                        },
                        mapOf("message" to cause.description)
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
                        HealthResponse(healthResult)
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
                            val device = Device(
                                requireNotNull(settings.deviceId),
                                deviceName,
                                Date(firstInstallTime),
                                Date(),
                                Date()
                            )

                            call.respond(listOf(device))
                        }
                    }
                    MessagesRoutes(applicationContext, get(), get(), get(), get()).let {
                        route("/message") {
                            it.register(this)
                        }
                        route("/messages") {
                            it.register(this)
                        }
                    }
                    route("/media") {
                        MediaRoutes(get()).register(this)
                    }
                    WebhooksRoutes(get(), get()).let {
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
                    route("/settings") {
                        me.stappmus.messagegateway.modules.localserver.routes.SettingsRoutes(get())
                            .register(this)
                    }
                    route("/docs") {
                        DocsRoutes(get()).register(this)
                    }
                    route("/auth") {
                        AuthRoutes().register(this)
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NotificationsService.NOTIFICATION_ID_LOCAL_SERVICE,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationsService.NOTIFICATION_ID_LOCAL_SERVICE, notification)
        }

        return START_STICKY
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

private val Throwable.description: String
    get() {
        return (localizedMessage ?: message ?: toString()) +
                (cause?.let { ": " + it.description } ?: "")
    }
