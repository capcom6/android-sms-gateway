package me.capcom.smsgateway.modules.localserver.routes

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Base64
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.core.readBytes
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.MmsAttachment
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.helpers.DateTimeParser
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.localserver.domain.GetMessageResponse
import me.capcom.smsgateway.modules.localserver.domain.MmsAttachmentMessage
import me.capcom.smsgateway.modules.localserver.domain.PostMessageRequest
import me.capcom.smsgateway.modules.localserver.domain.PostMessageResponse
import me.capcom.smsgateway.modules.localserver.domain.PostMessagesInboxExportRequest
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.messages.data.Message
import me.capcom.smsgateway.modules.messages.data.SendParams
import me.capcom.smsgateway.modules.messages.data.SendRequest
import me.capcom.smsgateway.modules.receiver.ReceiverService
import java.io.File
import java.security.MessageDigest
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
            val requestContentType = call.request.contentType().withoutParameters()
            if (requestContentType == ContentType.MultiPart.FormData) {
                handleMultipartMessagePost(call)
                return@post
            }

            val request = call.receive<PostMessageRequest>()
            handleJsonMessagePost(call, request)
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

    private suspend fun handleJsonMessagePost(call: ApplicationCall, request: PostMessageRequest) {
        if (request.deviceId?.let { it == settings.deviceId } == false) {
            call.respondBadRequest("Invalid device ID")
            return
        }

        val messageTypes = listOfNotNull(
            request.textMessage,
            request.dataMessage,
            request.message,
            request.mmsMessage,
        )
        when {
            messageTypes.isEmpty() -> {
                call.respondBadRequest("Must specify exactly one of: textMessage, dataMessage, mmsMessage, or message")
                return
            }

            messageTypes.size > 1 -> {
                call.respondBadRequest("Cannot specify multiple message types simultaneously")
                return
            }
        }

        request.message?.let { msg ->
            if (msg.isEmpty()) {
                call.respondBadRequest("Text message is empty")
                return
            }
        }

        request.dataMessage?.let { dataMsg ->
            if (dataMsg.port < 0 || dataMsg.port > 65535) {
                call.respondBadRequest("Port must be between 0 and 65535")
                return
            }

            if (dataMsg.data.isEmpty()) {
                call.respondBadRequest("Data message cannot be empty")
                return
            }
        }

        request.textMessage?.let { textMsg ->
            if (textMsg.text.isEmpty()) {
                call.respondBadRequest("Text message is empty")
                return
            }
        }

        request.mmsMessage?.let { mmsMessage ->
            if (mmsMessage.attachments.isEmpty()) {
                call.respondBadRequest("MMS message must contain at least one attachment")
                return
            }
        }

        if (request.phoneNumbers.isEmpty()) {
            call.respondBadRequest("phoneNumbers is empty")
            return
        }
        if (request.simNumber != null && request.simNumber < 1) {
            call.respondBadRequest("simNumber must be >= 1")
            return
        }
        val skipPhoneValidation =
            call.request.queryParameters["skipPhoneValidation"]
                ?.toBooleanStrict() ?: false

        val messageContent = try {
            when {
                request.message != null -> MessageContent.Text(request.message)
                request.textMessage != null -> MessageContent.Text(request.textMessage.text)
                request.dataMessage != null -> MessageContent.Data(
                    request.dataMessage.data,
                    request.dataMessage.port.toUShort()
                )

                request.mmsMessage != null -> MessageContent.Mms(
                    text = request.mmsMessage.text?.takeIf { it.isNotBlank() },
                    attachments = request.mmsMessage.attachments.map { buildAttachmentFromJson(it) },
                )

                else -> throw IllegalStateException("Unknown message type")
            }
        } catch (e: IllegalArgumentException) {
            call.respondBadRequest(e.message ?: "Invalid MMS attachment payload")
            return
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

        enqueueAndRespond(call, sendRequest, request.isEncrypted ?: false)
    }

    private suspend fun handleMultipartMessagePost(call: ApplicationCall) {
        val formValues = mutableMapOf<String, MutableList<String>>()
        val attachments = mutableListOf<MmsAttachment>()

        val multipart = call.receiveMultipart()
        while (true) {
            val part = multipart.readPart() ?: break
            when (part) {
                is PartData.FormItem -> {
                    val key = part.name?.takeIf { it.isNotBlank() }
                    if (key == null) {
                        part.dispose()
                        continue
                    }
                    formValues.getOrPut(key) { mutableListOf() }.add(part.value)
                }

                is PartData.FileItem -> {
                    val bytes = part.provider().readBytes()
                    if (bytes.isNotEmpty()) {
                        val mimeType = part.contentType?.toString()?.takeIf { it.isNotBlank() }
                            ?: "application/octet-stream"
                        attachments += storeOutgoingAttachment(bytes, part.originalFileName, mimeType)
                    }
                }

                else -> Unit
            }

            part.dispose()
        }

        val deviceId = formValues.lastValue("deviceId")
        if (deviceId != null && deviceId != settings.deviceId) {
            call.respondBadRequest("Invalid device ID")
            return
        }

        val phoneNumbers = parsePhoneNumbers(formValues["phoneNumbers"].orEmpty())
        if (phoneNumbers.isEmpty()) {
            call.respondBadRequest("phoneNumbers is empty")
            return
        }

        if (attachments.isEmpty()) {
            call.respondBadRequest("MMS message must contain at least one file attachment")
            return
        }

        val simNumberRaw = formValues.lastValue("simNumber")
        val simNumber = simNumberRaw?.toIntOrNull()
        if (simNumberRaw != null && simNumber == null) {
            call.respondBadRequest("simNumber must be an integer")
            return
        }
        if (simNumber != null && simNumber < 1) {
            call.respondBadRequest("simNumber must be >= 1")
            return
        }

        val priorityRaw = formValues.lastValue("priority")
        val priority = priorityRaw?.toByteOrNull()
        if (priorityRaw != null && priority == null) {
            call.respondBadRequest("priority must be a valid byte value")
            return
        }

        val withDeliveryReportRaw = formValues.lastValue("withDeliveryReport")
        val withDeliveryReport = withDeliveryReportRaw?.toBooleanStrictOrNull()
        if (withDeliveryReportRaw != null && withDeliveryReport == null) {
            call.respondBadRequest("withDeliveryReport must be true or false")
            return
        }

        val isEncryptedRaw = formValues.lastValue("isEncrypted")
        val isEncrypted = isEncryptedRaw?.toBooleanStrictOrNull()
        if (isEncryptedRaw != null && isEncrypted == null) {
            call.respondBadRequest("isEncrypted must be true or false")
            return
        }

        val ttlRaw = formValues.lastValue("ttl")
        val ttl = ttlRaw?.toLongOrNull()
        if (ttlRaw != null && ttl == null) {
            call.respondBadRequest("ttl must be a valid number of seconds")
            return
        }

        val validUntilRaw = formValues.lastValue("validUntil")
        val validUntil = validUntilRaw?.let { DateTimeParser.parseIsoDateTime(it) }
        if (validUntilRaw != null && validUntil == null) {
            call.respondBadRequest("validUntil must be a valid ISO date time")
            return
        }
        if (ttl != null && validUntil != null) {
            call.respondBadRequest("fields conflict: ttl and validUntil")
            return
        }

        val effectiveValidUntil = validUntil ?: ttl?.let {
            Date(System.currentTimeMillis() + (it * 1000L))
        }
        if (effectiveValidUntil?.before(Date()) == true) {
            call.respondBadRequest("message already expired")
            return
        }

        val text = formValues.lastValue("text") ?: formValues.lastValue("message")
        val skipPhoneValidation =
            call.request.queryParameters["skipPhoneValidation"]
                ?.toBooleanStrict() ?: false

        val sendRequest = SendRequest(
            EntitySource.Local,
            Message(
                id = formValues.lastValue("id") ?: NanoIdUtils.randomNanoId(),
                content = MessageContent.Mms(
                    text = text?.takeIf { it.isNotBlank() },
                    attachments = attachments,
                ),
                phoneNumbers = phoneNumbers,
                isEncrypted = isEncrypted ?: false,
                createdAt = Date(),
            ),
            SendParams(
                withDeliveryReport = withDeliveryReport ?: true,
                skipPhoneValidation = skipPhoneValidation,
                simNumber = simNumber,
                validUntil = effectiveValidUntil,
                priority = priority,
            )
        )

        enqueueAndRespond(call, sendRequest, isEncrypted ?: false)
    }

    private suspend fun enqueueAndRespond(
        call: ApplicationCall,
        sendRequest: SendRequest,
        isEncrypted: Boolean,
    ) {
        messagesService.enqueueMessage(sendRequest)

        val messageId = sendRequest.message.id
        call.respond(
            HttpStatusCode.Accepted,
            PostMessageResponse(
                id = messageId,
                deviceId = requireNotNull(settings.deviceId),
                state = ProcessingState.Pending,
                isHashed = false,
                isEncrypted = isEncrypted,
                recipients = sendRequest.message.phoneNumbers.map {
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

    private fun parsePhoneNumbers(values: List<String>): List<String> {
        return values
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun buildAttachmentFromJson(attachment: MmsAttachmentMessage): MmsAttachment {
        val mimeType = attachment.mimeType.trim()
        if (mimeType.isEmpty()) {
            throw IllegalArgumentException("MMS attachment mimeType is required")
        }

        attachment.data?.takeIf { it.isNotBlank() }?.let { data ->
            val decoded = try {
                Base64.decode(data, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("MMS attachment data must be valid Base64")
            }

            return storeOutgoingAttachment(
                bytes = decoded,
                originalFilename = attachment.filename,
                mimeType = mimeType,
                id = attachment.id,
            )
        }

        val downloadUrl = attachment.downloadUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("MMS attachment must provide either data or downloadUrl")

        return MmsAttachment(
            id = attachment.id ?: NanoIdUtils.randomNanoId(),
            mimeType = mimeType,
            filename = attachment.filename,
            size = attachment.size ?: 0L,
            width = attachment.width,
            height = attachment.height,
            durationMs = attachment.durationMs,
            sha256 = attachment.sha256,
            downloadUrl = downloadUrl,
        )
    }

    private fun storeOutgoingAttachment(
        bytes: ByteArray,
        originalFilename: String?,
        mimeType: String,
        id: String? = null,
    ): MmsAttachment {
        val attachmentId = id ?: NanoIdUtils.randomNanoId()
        val targetFile = createOutgoingAttachmentFile(attachmentId, originalFilename)
        targetFile.writeBytes(bytes)

        val metadata = resolveMediaMetadata(targetFile, bytes, mimeType)

        return MmsAttachment(
            id = attachmentId,
            mimeType = mimeType,
            filename = originalFilename ?: targetFile.name,
            size = bytes.size.toLong(),
            width = metadata.width,
            height = metadata.height,
            durationMs = metadata.durationMs,
            sha256 = sha256(bytes),
            downloadUrl = targetFile.toURI().toString(),
        )
    }

    private fun createOutgoingAttachmentFile(id: String, originalFilename: String?): File {
        val directory = File(context.filesDir, "outgoing-mms").apply {
            mkdirs()
        }
        val sanitizedName = originalFilename
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "attachment"

        return File(directory, "$id-$sanitizedName")
    }

    private fun resolveMediaMetadata(file: File, bytes: ByteArray, mimeType: String): MediaMetadata {
        val normalized = mimeType.lowercase()

        return when {
            normalized.startsWith("image/") -> {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                MediaMetadata(
                    width = options.outWidth.takeIf { it > 0 },
                    height = options.outHeight.takeIf { it > 0 },
                    durationMs = null,
                )
            }

            normalized.startsWith("video/") || normalized.startsWith("audio/") -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    MediaMetadata(
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            ?.toIntOrNull(),
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            ?.toIntOrNull(),
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull(),
                    )
                } catch (_: Exception) {
                    MediaMetadata(null, null, null)
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }

            else -> MediaMetadata(null, null, null)
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private suspend fun ApplicationCall.respondBadRequest(message: String) {
        respond(
            HttpStatusCode.BadRequest,
            mapOf("message" to message)
        )
    }

    private fun Map<String, MutableList<String>>.lastValue(key: String): String? {
        return this[key]?.lastOrNull()
    }

    private data class MediaMetadata(
        val width: Int?,
        val height: Int?,
        val durationMs: Long?,
    )

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
