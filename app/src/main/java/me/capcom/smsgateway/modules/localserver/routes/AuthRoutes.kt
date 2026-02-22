package me.capcom.smsgateway.modules.localserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.capcom.smsgateway.modules.localserver.auth.AuthScopes
import me.capcom.smsgateway.modules.localserver.auth.JwtService
import me.capcom.smsgateway.modules.localserver.auth.requireScope
import me.capcom.smsgateway.modules.localserver.domain.TokenRequest
import me.capcom.smsgateway.modules.localserver.domain.TokenResponse

class AuthRoutes(
    private val jwtService: JwtService,
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
            if (!requireScope(AuthScopes.TOKENS_MANAGE)) return@post
            val request = call.receive<TokenRequest>()
            val token = jwtService.generateToken(request.scopes, request.ttl)
            call.respond(
                HttpStatusCode.Created,
                TokenResponse(
                    id = token.id,
                    tokenType = "Bearer",
                    accessToken = token.accessToken,
                    expiresAt = token.expiresAt,
                )
            )
        }
        delete("/{jti}") {
            if (!requireScope(AuthScopes.TOKENS_MANAGE)) return@delete
            val jti = call.parameters["jti"]?.trim()
            if (jti.isNullOrEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "jti is required"))
                return@delete
            }

            jwtService.revokeToken(jti)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
