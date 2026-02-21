package me.capcom.smsgateway.modules.localserver.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext

suspend fun PipelineContext<Unit, ApplicationCall>.requireScope(scope: String): Boolean {
    if (call.principal<UserIdPrincipal>() != null) {
        return true
    }

    val jwtPrincipal = call.principal<JWTPrincipal>()
    if (jwtPrincipal == null) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
        return false
    }

    val scopes = jwtPrincipal.payload
        .getClaim("scopes")
        .asList(String::class.java)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    if (AuthScopes.ALL_ANY in scopes || scope in scopes) {
        return true
    }

    call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Insufficient permissions"))
    return false
}
