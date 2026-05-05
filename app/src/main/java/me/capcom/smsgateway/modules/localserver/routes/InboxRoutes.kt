package me.capcom.smsgateway.modules.localserver.routes

import android.content.Context
import android.net.Uri
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import me.capcom.smsgateway.helpers.DateTimeParser
import me.capcom.smsgateway.modules.incoming.IncomingMessagesService
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.localserver.auth.AuthScopes
import me.capcom.smsgateway.modules.localserver.auth.requireScope
import me.capcom.smsgateway.modules.localserver.domain.PostMessagesInboxExportRequest
import me.capcom.smsgateway.modules.mms.MmsAttachmentStorage
import me.capcom.smsgateway.modules.receiver.ReceiverService
import java.util.Date

class InboxRoutes(
    private val context: Context,
    private val incomingMessagesService: IncomingMessagesService,
    private val receiverService: ReceiverService,
    private val attachmentStorage: MmsAttachmentStorage,
    private val settings: LocalServerSettings,
) {
    fun register(routing: Route) {
        routing.inboxRoutes(context)
    }

    private fun Route.inboxRoutes(context: Context) {
        get {
            if (!requireScope(AuthScopes.InboxList)) return@get

            val rawType = call.request.queryParameters["type"]?.takeIf { it.isNotBlank() }
            val type = try {
                rawType?.let { IncomingMessageType.valueOf(it) }
            } catch (_: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Invalid type")
                )
                return@get
            }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            if (limit !in 1..500) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "limit must be between 1 and 500")
                )
                return@get
            }

            if (offset < 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "offset must be >= 0")
                )
                return@get
            }

            val fromRaw = call.request.queryParameters["from"]
            val toRaw = call.request.queryParameters["to"]

            val from = if (fromRaw == null) {
                0L
            } else {
                DateTimeParser.parseIsoDateTime(fromRaw)?.time ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Invalid from datetime")
                    )
                    return@get
                }
            }

            val to = if (toRaw == null) {
                Date().time
            } else {
                DateTimeParser.parseIsoDateTime(toRaw)?.time ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Invalid to datetime")
                    )
                    return@get
                }
            }

            val deviceId = call.request.queryParameters["deviceId"]
            if (deviceId != null && deviceId != settings.deviceId) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Invalid device ID")
                )
                return@get
            }

            if (from > to) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Start date cannot be after end date")
                )
                return@get
            }

            val total = try {
                incomingMessagesService.count(type, from, to)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to count incoming messages: ${e.message}")
                )
                return@get
            }

            val messages = try {
                incomingMessagesService.select(type, from, to, limit, offset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to retrieve incoming messages: ${e.message}")
                )
                return@get
            }

            call.response.headers.append("X-Total-Count", total.toString())

            call.respond(messages.map { it.toDomain() } as GetIncomingMessagesResponse)
        }

        get("{id}") {
            if (!requireScope(AuthScopes.InboxRead)) return@get
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val message = try {
                incomingMessagesService.getById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@get call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to e.message)
                )
            }

            val detail = InboxMessageDetail(
                id = message.id,
                type = message.type,
                sender = message.sender,
                recipient = message.recipient,
                simNumber = message.simNumber,
                contentPreview = message.contentPreview,
                createdAt = Date(message.createdAt),
                attachments = listAttachmentRefs(id),
            )
            call.respond(detail)
        }

        get("{id}/attachments/{partId}") {
            if (!requireScope(AuthScopes.InboxRead)) return@get
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val partId = call.parameters["partId"]?.toLongOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "partId must be a number")
                )

            val attachment = attachmentStorage.find(id, partId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            val file = attachment.file

            val contentType = parseContentType(attachment.contentType)
            call.response.header(
                "Content-Disposition",
                "attachment; filename=\"${attachment.displayName}\""
            )
            call.respondBytes(file.readBytes(), contentType)
        }

        post("refresh") {
            if (!requireScope(AuthScopes.InboxRefresh)) return@post

            val request = try {
                call.receive<PostMessagesInboxExportRequest>().validate()
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to (e.message ?: "Invalid request"))
                )
                return@post
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid request body"))
                return@post
            }

            try {
                receiverService.export(context, request.period, false)
                call.respond(HttpStatusCode.Accepted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to refresh inbox: ${e.message}")
                )
            }
        }
    }

    data class InboxMessage(
        val id: String,
        val type: IncomingMessageType,
        val sender: String,
        val recipient: String?,
        val simNumber: Int?,
        val contentPreview: String,
        val createdAt: Date,
    )

    data class InboxMessageDetail(
        val id: String,
        val type: IncomingMessageType,
        val sender: String,
        val recipient: String?,
        val simNumber: Int?,
        val contentPreview: String,
        val createdAt: Date,
        val attachments: List<AttachmentRef>,
    )

    data class AttachmentRef(
        val partId: Long,
        val name: String,
        val size: Long,
        val contentType: String,
        val url: String,
    )

    private fun listAttachmentRefs(messageId: String): List<AttachmentRef> {
        return attachmentStorage.list(messageId).map { attachment ->
            AttachmentRef(
                partId = attachment.partId,
                name = attachment.displayName,
                size = attachment.file.length(),
                contentType = attachment.contentType,
                url = "/inbox/${Uri.encode(messageId)}/attachments/${attachment.partId}",
            )
        }.sortedBy { it.partId }
    }

    private fun parseContentType(raw: String): ContentType {
        return runCatching { ContentType.parse(raw) }
            .getOrDefault(ContentType.Application.OctetStream)
    }

    private fun IncomingMessage.toDomain() = InboxMessage(
        id = id,
        type = type,
        sender = sender,
        recipient = recipient,
        simNumber = simNumber,
        contentPreview = contentPreview,
        createdAt = Date(createdAt),
    )
}

typealias GetIncomingMessagesResponse = List<InboxRoutes.InboxMessage>
