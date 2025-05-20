package me.capcom.smsgateway.modules.localserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import me.capcom.smsgateway.modules.settings.SettingsService

class SettingsRoutes(
    private val settingsService: SettingsService
) {
    fun register(routing: Route) {
        routing.apply {
            settingsRoutes()
        }
    }

    private fun Route.settingsRoutes() {
        get {
            try {
                val settings = settingsService.getAll()
                call.respond(settings)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to get settings: ${e.message}")
                )
            }
        }
        patch {
            try {
                val settings = call.receive<Map<String, *>>()

                settingsService.apply(settings)

                call.respond(
                    HttpStatusCode.OK,
                    settingsService.getAll()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Invalid request: ${e.message}")
                )
            }
        }
    }
}
