package me.capcom.smsgateway.modules.localserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import me.capcom.smsgateway.modules.webhooks.WebHooksService

class WebhooksRoutes(
    private val webHooksService: WebHooksService,
) {
    fun register(app: Application) {
        app.routing {
            route("/webhook") {
                webhooksRoutes()
            }
            route("/webhooks") {
                webhooksRoutes()
            }
        }
    }

    private fun Route.webhooksRoutes() {
        get {
            call.respond(HttpStatusCode.OK)
        }
        post {
            call.respond(HttpStatusCode.Created)
        }
        delete("/{id}") {
            call.respond(HttpStatusCode.NoContent)
        }
    }
}