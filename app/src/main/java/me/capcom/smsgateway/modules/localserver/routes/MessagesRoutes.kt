package me.capcom.smsgateway.modules.localserver.routes

import android.content.Context
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.helpers.DateTimeParser
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.localserver.domain.GetMessageResponse
import me.capcom.smsgateway.modules.localserver.domain.PostMessageRequest
import me.capcom.smsgateway.modules.localserver.domain.PostMessageResponse
import me.capcom.smsgateway.modules.localserver.domain.PostMessagesInboxExportRequest
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.messages.data.Message
import me.capcom.smsgateway.modules.messages.data.SendParams
import me.capcom.smsgateway.modules.messages.data.SendRequest
import me.capcom.smsgateway.modules.receiver.ReceiverService
import java.util.Date

class MessagesRoutes(
    private val context: Context,
    private val messagesService: MessagesService,
    private val receiverService: ReceiverService,
    private val settings: LocalServerSettings,
) {
    fun register(routing: Route) {
        routing.apply {
            messagesRoutes()
            route("/inbox") {
                inboxRoutes(context)
            }
        }
    }

    private fun Route.messagesRoutes() {
        get {
            // Parse and validate parameters
            val state = call.request.queryParameters["state"]?.takeIf { it.isNotEmpty() }
                ?.let { ProcessingState.valueOf(it) }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            // Parse date range parameters
            val from = call.request.queryParameters["from"]?.let {
                DateTimeParser.parseIsoDateTime(it)?.time
            } ?: 0
            val to = call.request.queryParameters["to"]?.let {
                DateTimeParser.parseIsoDateTime(it)?.time
            } ?: Date().time

            val deviceId = call.request.queryParameters["deviceId"]
            if (deviceId != null && deviceId != settings.deviceId) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Invalid device ID")
                )
                return@get
            }

            // Ensure start date is before end date
            if (from > to) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Start date cannot be after end date")
                )
                return@get
            }

            // Get total count for pagination
            val total = try {
                messagesService.countMessages(EntitySource.Local, state, from, to)
            } catch (e: Throwable) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to count messages: ${e.message}")
                )
                return@get
            }

            // Get messages with pagination
            val messages = try {
                messagesService.selectMessages(EntitySource.Local, state, from, to, limit, offset)
            } catch (e: Throwable) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to retrieve messages: ${e.message}")
                )
                return@get
            }

            call.response.headers.append("X-Total-Count", total.toString())

            call.respond(
                messages.map { it.toDomain(requireNotNull(settings.deviceId)) } as GetMessageResponse
            )
        }

        post {
            val request = call.receive<PostMessageRequest>()

            if (request.deviceId?.let { it == settings.deviceId } == false) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Invalid device ID")
                )
                return@post
            }

            val messageTypes =
                listOfNotNull(request.textMessage, request.dataMessage, request.message)
            when {
                messageTypes.isEmpty() -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Must specify exactly one of: textMessage, dataMessage, or message")
                    )
                    return@post
                }

                messageTypes.size > 1 -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Cannot specify multiple message types simultaneously")
                    )
                    return@post
                }
            }

            // Validate message parameters
            request.message?.let { msg ->
                // Text validation
                if (msg.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Text message is empty")
                    )
                    return@post
                }
            }

            // Validate data message parameters
            request.dataMessage?.let { dataMsg ->
                // Port validation
                if (dataMsg.port < 0 || dataMsg.port > 65535) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Port must be between 0 and 65535")
                    )
                    return@post
                }

                // Data validation (only for non-empty check)
                if (dataMsg.data.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Data message cannot be empty")
                    )
                    return@post
                }
            }

            // Validate text message parameters
            request.textMessage?.let { textMsg ->
                // Text validation
                if (textMsg.text.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Text message is empty")
                    )
                    return@post
                }
            }

            // Existing validation
            if (request.phoneNumbers.isEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "phoneNumbers is empty")
                )
                return@post
            }
            if (request.simNumber != null && request.simNumber < 1) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "simNumber must be >= 1")
                )
                return@post
            }
            val skipPhoneValidation =
                call.request.queryParameters["skipPhoneValidation"]
                    ?.toBooleanStrict() ?: false

            // Create message content based on type
            val messageContent = when {
                request.message != null -> {
                    MessageContent.Text(request.message)
                }

                request.textMessage != null -> {
                    MessageContent.Text(request.textMessage.text)
                }

                request.dataMessage != null -> {
                    MessageContent.Data(
                        request.dataMessage.data,
                        request.dataMessage.port.toUShort()
                    )
                }

                else -> {
                    // This case should be caught by validation, but just in case
                    throw IllegalStateException("Unknown message type")
                }
            }

            val sendRequest = SendRequest(
                EntitySource.Local,
                Message(
                    request.id ?: NanoIdUtils.randomNanoId(),
                    content = messageContent,
                    phoneNumbers = request.phoneNumbers,
                    isEncrypted = request.isEncrypted ?: false,
                    createdAt = Date(),
                ),
                SendParams(
                    request.withDeliveryReport ?: true,
                    skipPhoneValidation = skipPhoneValidation,
                    simNumber = request.simNumber,
                    validUntil = request.validUntil,
                    priority = request.priority,
                )
            )
            messagesService.enqueueMessage(sendRequest)

            val messageId = sendRequest.message.id

            call.respond(
                HttpStatusCode.Accepted,
                PostMessageResponse(
                    id = messageId,
                    deviceId = requireNotNull(settings.deviceId),
                    state = ProcessingState.Pending,
                    isHashed = false,
                    isEncrypted = request.isEncrypted ?: false,
                    recipients = request.phoneNumbers.map {
                        me.capcom.smsgateway.modules.localserver.domain.Message.Recipient(
                            it,
                            ProcessingState.Pending,
                            null
                        )
                    },
                    states = mapOf(ProcessingState.Pending to Date())
                )
            )
        }
        get("{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val message = try {
                messagesService.getMessage(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
            } catch (e: Throwable) {
                return@get call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to e.message)
                )
            }

            call.respond(
                message.toDomain(requireNotNull(settings.deviceId)) as PostMessageResponse
            )
        }
    }

    private fun Route.inboxRoutes(context: Context) {
        post("export") {
            val request = call.receive<PostMessagesInboxExportRequest>().validate()
            try {
                receiverService.export(context, request.period)
                call.respond(HttpStatusCode.Accepted)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to export inbox: ${e.message}")
                )
            }
        }
    }

    private fun MessageWithRecipients.toDomain(deviceId: String) =
        me.capcom.smsgateway.modules.localserver.domain.Message(
            id = message.id,
            deviceId = deviceId,
            state = message.state,
            isHashed = false,
            isEncrypted = message.isEncrypted,
            recipients = recipients.map {
                me.capcom.smsgateway.modules.localserver.domain.Message.Recipient(
                    it.phoneNumber,
                    it.state,
                    it.error
                )
            },
            states = states.associate {
                it.state to Date(it.updatedAt)
            }
        )
}