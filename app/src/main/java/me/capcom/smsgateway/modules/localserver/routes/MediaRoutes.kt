package me.capcom.smsgateway.modules.localserver.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.capcom.smsgateway.modules.media.MediaService

class MediaRoutes(
    private val mediaService: MediaService,
) {
    fun register(routing: Route) {
        routing.get("{id}") {
            val mediaId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Missing media id"))
                return@get
            }

            val expires = call.request.queryParameters["expires"]?.toLongOrNull()
            val token = call.request.queryParameters["token"]

            val media = mediaService.resolveDownload(mediaId, expires, token) ?: run {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Invalid or expired token"))
                return@get
            }

            media.filename?.let {
                val escaped = it.replace("\"", "")
                call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"$escaped\"")
            }

            val contentType = try {
                ContentType.parse(media.mimeType)
            } catch (_: Exception) {
                ContentType.Application.OctetStream
            }

            call.respondBytes(
                bytes = media.bytes,
                contentType = contentType,
                status = HttpStatusCode.OK,
            )
        }
    }
}
