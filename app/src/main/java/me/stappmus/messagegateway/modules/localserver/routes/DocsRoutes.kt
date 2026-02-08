package me.stappmus.messagegateway.modules.localserver.routes

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.defaultForFilePath
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.pipeline.PipelineContext
import java.io.IOException

class DocsRoutes(
    private val applicationContext: Context,
) {

    fun register(routing: Route) {
        routing.apply {
            docsRoutes()
        }
    }

    private fun Route.docsRoutes() {
        get {
            redirect()
        }
        get("/") {
            redirect()
        }
        get("{path...}") {
            val path =
                call.parameters.getAll("path")?.joinToString("/")
                    ?.takeIf { it.isNotBlank() } ?: "index.html"
            // Prevent path traversal attacks
            if (path.contains("..")) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val assetPath = "api/$path"

            try {
                val inputStream = applicationContext.assets.open(assetPath)
                val bytes = inputStream.readBytes()
                call.respondBytes(
                    bytes,
                    ContentType.defaultForFilePath(assetPath)
                )
            } catch (e: IOException) {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.redirect() {
        call.respondRedirect(true) {
            appendPathSegments("index.html")
        }
    }
}