package me.capcom.smsgateway.modules.localserver.routes

import android.content.Context
import android.os.Build
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.device.DeviceService
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.localserver.auth.AuthScopes
import me.capcom.smsgateway.modules.localserver.auth.requireScope
import me.capcom.smsgateway.modules.localserver.domain.Device
import java.util.Date

class DeviceRoutes(
    private val context: Context,
    private val deviceService: DeviceService,
    private val settings: LocalServerSettings,
) {
    fun register(routing: Route) {
        routing.apply {
            deviceRoutes(context)
        }
    }

    private fun Route.deviceRoutes(context: Context) {
        get {
            if (!requireScope(AuthScopes.DevicesList)) return@get

            val firstInstallTime = context.packageManager.getPackageInfo(
                context.packageName,
                0
            ).firstInstallTime
            val simCards = SubscriptionsHelper.getActiveSimCards(context)
            val key = deviceService.ensureKey()

            val device = Device(
                publicKey = key?.publicKeyBase64,
                keyVersion = key?.keyVersion,
                id = requireNotNull(settings.deviceId),
                name = "${Build.MANUFACTURER}/${Build.PRODUCT}",
                createdAt = Date(firstInstallTime),
                updatedAt = Date(),
                lastSeen = Date(),
                simCards = simCards,
            )

            call.respond(listOf(device))
        }
    }
}
