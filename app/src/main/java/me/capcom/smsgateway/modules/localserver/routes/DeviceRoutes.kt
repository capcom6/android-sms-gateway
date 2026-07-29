package me.capcom.smsgateway.modules.localserver.routes

import android.content.Context
import android.os.Build
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.encryption.E2EKeyService
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.localserver.auth.AuthScopes
import me.capcom.smsgateway.modules.localserver.auth.requireScope
import me.capcom.smsgateway.modules.localserver.domain.Device
import java.util.Date

class DeviceRoutes(
    private val applicationContext: Context,
    private val settings: LocalServerSettings,
    private val e2eKeyService: E2EKeyService,
) {
    fun register(routing: Route) {
        routing.apply {
            deviceRoutes()
        }
    }

    private fun Route.deviceRoutes() {
        get {
            if (!requireScope(AuthScopes.DevicesList)) return@get
            val firstInstallTime = applicationContext.packageManager.getPackageInfo(
                applicationContext.packageName,
                0
            ).firstInstallTime
            val deviceName = "${Build.MANUFACTURER}/${Build.PRODUCT}"
            val simCards = SubscriptionsHelper.getActiveSimCards(applicationContext)
            val currentKey = e2eKeyService.ensureKey()
            val device = Device(
                requireNotNull(settings.deviceId),
                deviceName,
                Date(firstInstallTime),
                Date(),
                Date(),
                simCards,
                publicKey = currentKey?.publicKeyBase64,
                keyVersion = currentKey?.keyVersion,
            )

            call.respond(listOf(device))
        }
    }
}
