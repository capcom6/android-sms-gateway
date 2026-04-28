package me.capcom.smsgateway.modules.localserver.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext

suspend fun PipelineContext<Unit, ApplicationCall>.requireScope(
    scope: AuthScopes,
    exact: Boolean = false
): Boolean {
    if (call.principal<UserIdPrincipal>() != null && !exact) {
        return true
    }

    val jwtPrincipal = call.principal<JWTPrincipal>()
    if (jwtPrincipal == null) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
        return false
    }

    val scopes = try {
        jwtPrincipal.getListClaim("scopes", String::class)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) {
        android.util.Log.d("ScopeAuthorization", "Failed to parse scopes claim", e)
        emptyList()
    }

    if (exact && scopes.singleOrNull() != scope.value) {
        call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Insufficient permissions"))
        return false
    }

    if (AuthScopes.AllAny.value in scopes || scope.value in scopes) {
        return true
    }

    call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Insufficient permissions"))
    return false
}
