package me.stappmus.messagegateway.modules.localserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route

class AuthRoutes(
) {
    fun register(routing: Route) {
        routing.apply {
            route("token") {
                tokenRoutes()
            }
        }
    }

    private fun Route.tokenRoutes() {
        post {
            call.respond(HttpStatusCode.NotImplemented)
        }
        delete("/{jti}") {
            call.respond(HttpStatusCode.NotImplemented)
        }
    }
}