package me.capcom.smsgateway.modules.localserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.capcom.smsgateway.data.entities.TokenUse
import me.capcom.smsgateway.modules.localserver.auth.AuthScopes
import me.capcom.smsgateway.modules.localserver.auth.JwtService
import me.capcom.smsgateway.modules.localserver.auth.RefreshTokenException
import me.capcom.smsgateway.modules.localserver.auth.requireScope
import me.capcom.smsgateway.modules.localserver.domain.TokenRequest
import me.capcom.smsgateway.modules.localserver.domain.TokenResponse
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry

class AuthRoutes(
    private val jwtService: JwtService,
    private val logsService: LogsService,
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
            if (!requireScope(AuthScopes.TokensManage)) return@post
            val request = call.receive<TokenRequest>()
            val tokens = jwtService.generateTokenPair(request.scopes, request.ttl)
            call.respond(
                HttpStatusCode.Created,
                TokenResponse(
                    id = tokens.access.id,
                    tokenType = "Bearer",
                    accessToken = tokens.access.token,
                    expiresAt = tokens.access.expiresAt,
                    refreshToken = tokens.refresh.token,
                )
            )
        }

        post("/refresh") {
            if (!requireScope(AuthScopes.TokensRefresh, exact = true)) return@post

            val jwtPrincipal =
                call.principal<JWTPrincipal>()
            if (jwtPrincipal == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
                return@post
            }

            if (jwtPrincipal.getClaim("token_use", String::class) != TokenUse.Refresh.value) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
                return@post
            }

            val jwtID = jwtPrincipal.jwtId
            if (jwtID.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
                return@post
            }
            val originalScopes = try {
                jwtPrincipal.getListClaim("original_scopes", String::class)
            } catch (e: Exception) {
                logsService.insert(
                    LogEntry.Priority.WARN,
                    "AuthRoutes",
                    "Failed to parse original_scopes claim",
                    mapOf(
                        "exception" to e.message,
                    ),
                )
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
                return@post
            }

            val refreshed = try {
                jwtService.refreshTokenPair(jwtID, originalScopes)
            } catch (e: RefreshTokenException) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("message" to (e.message ?: "Unauthorized"))
                )
                return@post
            }

            call.respond(
                HttpStatusCode.OK,
                TokenResponse(
                    id = refreshed.access.id,
                    tokenType = "Bearer",
                    accessToken = refreshed.access.token,
                    expiresAt = refreshed.access.expiresAt,
                    refreshToken = refreshed.refresh.token,
                )
            )
        }

        delete("/{jti}") {
            if (!requireScope(AuthScopes.TokensManage)) return@delete
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
