package me.capcom.smsgateway.modules.gateway

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.hostWithPort
import io.ktor.serialization.gson.gson
import io.ktor.util.encodeBase64
import kotlinx.coroutines.CancellationException
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.extensions.configure
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import java.util.Date

class GatewayApi(
    private val baseUrl: String,
    private val privateToken: String?
) {
    val hostname: String
        get() = Url(baseUrl).hostWithPort

    private val client = HttpClient(OkHttp) {
        install(UserAgent) {
            agent = "me.capcom.smsgateway/" + BuildConfig.VERSION_NAME
        }
        install(ContentNegotiation) {
            gson {
                configure()
            }
        }
        expectSuccess = true
    }

    // Gson instance mirroring the ContentNegotiation configuration above; used
    // for the per-element batch deserialization in getMessages.
    private val messageGson: Gson by lazy { GsonBuilder().configure().create() }

    suspend fun getDevice(token: String?): DeviceGetResponse {
        return client.get("$baseUrl/device") {
            token?.let { bearerAuth(it) }
        }.body()
    }

    suspend fun deviceRegister(
        request: DeviceRegisterRequest,
        credentials: Pair<String, String>?
    ): DeviceRegisterResponse {
        return client.post("$baseUrl/device") {
            when {
                credentials != null -> basicAuth(credentials.first, credentials.second)
                privateToken != null -> bearerAuth(privateToken)
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deviceRegister(
        request: DeviceRegisterRequest,
        code: String
    ): DeviceRegisterResponse {
        return client.post("$baseUrl/device") {
            header("Authorization", "Code $code")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun devicePatch(token: String, request: DevicePatchRequest) {
        client.patch("$baseUrl/device") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /**
     * Fetches the pending batch with per-element deserialization (T7).
     *
     * The response body is read as raw text and each array element is
     * deserialized on its own ([parseMessageBatch]), so ONE malformed
     * element (any per-element JsonSyntaxException: bad timestamp value,
     * wrong field type, ...) can no longer abort the whole batch before any
     * local processing or ack happens - the pre-T7 `.body()` deserialized
     * the entire List<Message> first and threw the whole window away.
     * Body-level faults (whole payload unparseable / not an array) still
     * throw and keep the existing retry semantics.
     */
    suspend fun getMessages(token: String, processingOrder: ProcessingOrder): MessageBatch {
        val body = client.get("$baseUrl/message") {
            parameter("order", processingOrder)
            bearerAuth(token)
        }.bodyAsText()

        return parseMessageBatch(body, messageGson)
    }

    suspend fun patchMessages(token: String, request: List<MessagePatchRequest>) {
        client.patch("$baseUrl/message") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun postInbox(token: String, messages: List<InboxMessage>) {
        client.post("$baseUrl/inbox") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(messages)
        }
    }

    suspend fun getWebHooks(token: String): List<WebHook> {
        return client.get("$baseUrl/webhooks") {
            bearerAuth(token)
        }.body()
    }

    suspend fun getUserCode(credentials: Pair<String, String>): GetUserCodeResponse {
        return client.get("$baseUrl/user/code") {
            basicAuth(credentials.first, credentials.second)
        }.body()
    }

    suspend fun changeUserPassword(token: String, request: PasswordChangeRequest) {
        client.patch("$baseUrl/user/password") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getSettings(token: String): Map<String, *> {
        return client.get("$baseUrl/settings") {
            bearerAuth(token)
        }.body()
    }

    ///////////////////////////////////////////////////////////////////////////
    private fun HttpRequestBuilder.bearerAuth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun HttpRequestBuilder.basicAuth(username: String, password: String) {
        header(HttpHeaders.Authorization, "Basic ${"$username:$password".encodeBase64()}")
    }

    ///////////////////////////////////////////////////////////////////////////
    data class DeviceGetResponse(
        val externalIp: String,
    )

    data class DeviceRegisterRequest(
        val name: String,
        val pushToken: String?,
        val simCards: List<SimCard>,
        val publicKey: String? = null,
        val keyVersion: Int? = null,
    )

    data class DeviceRegisterResponse(
        val id: String,
        val token: String,
        val login: String,
        val password: String?,
    )

    data class DevicePatchRequest(
        val id: String,
        val pushToken: String?,
        val publicKey: String?,
        val keyVersion: Int?,
        val simCards: List<SimCard>?,
    )

    data class MessagePatchRequest(
        val id: String,
        val state: ProcessingState,
        val recipients: List<RecipientState>,
        val states: Map<ProcessingState, Date>
    )

    data class PasswordChangeRequest(
        val currentPassword: String,
        val newPassword: String
    )

    data class GetUserCodeResponse(
        val code: String,
        val validUntil: Date
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////
    sealed class MessageContent {
        class Text(
            val text: String,
        ) : MessageContent()

        class Data(
            val data: String,
            val port: UShort,
        ) : MessageContent()

        class Mms(
            val subject: String?,
            val text: String?,
            // Gson can leave this null when the field is omitted (text-only
            // MMS), despite the non-null Kotlin type. Normalize via the
            // public accessor below so callers never see null.
            @SerializedName("attachments")
            private val _attachments: List<Attachment>?,
        ) : MessageContent() {
            val attachments: List<Attachment>
                get() = _attachments.orEmpty()

            class Attachment(
                val contentType: String,
                val name: String?,
                val data: String,
            )
        }
    }

    data class Message(
        val id: String,
        @SerializedName("textMessage")
        val _textMessage: MessageContent.Text?,
        @SerializedName("dataMessage")
        val _dataMessage: MessageContent.Data?,
        @SerializedName("mmsMessage")
        val _mmsMessage: MessageContent.Mms?,
        val phoneNumbers: List<String>,
        val simNumber: Int?,
        val withDeliveryReport: Boolean?,
        val isEncrypted: Boolean?,
        val validUntil: Date?,
        val scheduleAt: Date?,
        val priority: Byte?,
        val state: MessageState?,
        val createdAt: Date?,

        @SerializedName("message")
        val _message: String?,
    ) {
        val content: MessageContent
            get() = this._dataMessage
                ?: this._textMessage
                ?: this._mmsMessage
                ?: _message?.let { MessageContent.Text(it) }
                ?: throw RuntimeException("Invalid message content")
    }

    data class RecipientState(
        val phoneNumber: String,
        val state: ProcessingState,
        val error: String?,
    )

    /**
     * Wire-level message state as reported by the gateway server. Mirrors the
     * server vocabulary exactly, including Cancelling - a server-side async
     * cancellation marker resolved in [me.capcom.smsgateway.modules.gateway.GatewayService]
     * that never enters the local domain (kept separate from the local
     * [ProcessingState], which never contains Cancelling).
     */
    enum class MessageState {
        @SerializedName("Pending")
        Pending,

        @SerializedName("Cancelling")
        Cancelling,

        @SerializedName("Cancelled")
        Cancelled,

        @SerializedName("Processed")
        Processed,

        @SerializedName("Sent")
        Sent,

        @SerializedName("Delivered")
        Delivered,

        @SerializedName("Failed")
        Failed
    }

    data class WebHook(
        val id: String,
        val url: String,
        val event: WebHookEvent,
    )

    enum class ProcessingOrder {
        @SerializedName("lifo")
        LIFO,

        @SerializedName("fifo")
        FIFO;

        override fun toString(): String {
            return this.name.lowercase()
        }
    }

    data class SimCard(
        val slotIndex: Int,
        val simNumber: Int,
        val phoneNumber: String?,
        val carrierName: String?,
        val iccid: String?,
    )

    object InboxMessageType {
        const val SMS = "SMS"
        const val DATA_SMS = "DATA_SMS"
        const val MMS = "MMS"
        const val MMS_DOWNLOADED = "MMS_DOWNLOADED"
    }

    data class InboxMessage(
        val id: String,
        val type: String,
        val sender: String,
        val recipient: String?,
        val simNumber: Int?,
        val content: String,
        val isEncrypted: Boolean = true,
        val createdAt: Date,
        val attachments: List<InboxMessageAttachment>?,
    )

    data class InboxMessageAttachment(
        val partId: Long,
        val contentType: String,
        val name: String,
        val size: Long?,
        val data: String, // base64 of raw bytes
    )
}

/**
 * Result of a per-element batch parse (T7): the deserialized messages plus a
 * report of every element that could NOT be deserialized.
 *
 * The policy is isolate-and-skip-with-logging (conservative default per OQ-B,
 * a permanent gap): a poison element never aborts the batch, and it is never
 * silently dropped - every skip carries index, best-effort server row id, and
 * the cause so the caller can log it.
 */
data class MessageBatch(
    val messages: List<GatewayApi.Message>,
    val skipped: List<SkippedMessage>,
)

/** One element that failed to deserialize, reported instead of dropped. */
data class SkippedMessage(
    val index: Int,
    val id: String?,
    val error: Throwable,
)

/**
 * Deserializes a mobile GET /message response body element-by-element.
 *
 * - Element-level faults (poison timestamp, bad shape of one object, JSON
 *   null element) are isolated: the element goes to [MessageBatch.skipped]
 *   with its index and best-effort id, the siblings survive.
 * - Body-level faults (whole payload unparseable, payload not a JSON array)
 *   still throw [JsonParseException] so callers keep the historical
 *   abort-and-retry semantics for transport/shape errors.
 * - CancellationException is never swallowed.
 */
internal fun parseMessageBatch(raw: String, gson: Gson): MessageBatch {
    val root = try {
        JsonParser.parseString(raw)
    } catch (e: CancellationException) {
        throw e
    } catch (th: Throwable) {
        throw JsonParseException("Unparseable message batch body", th)
    }

    if (!root.isJsonArray) {
        throw JsonParseException(
            "Message batch body is not a JSON array: ${root.javaClass.simpleName}"
        )
    }

    val messages = ArrayList<GatewayApi.Message>()
    val skipped = ArrayList<SkippedMessage>()

    root.asJsonArray.forEachIndexed { index, element ->
        try {
            if (element.isJsonNull) {
                throw JsonParseException("null element at index $index")
            }
            val message = gson.fromJson(element, GatewayApi.Message::class.java)
                ?: throw JsonParseException("null message at index $index")
            messages.add(message)
        } catch (e: CancellationException) {
            throw e
        } catch (th: Throwable) {
            skipped.add(SkippedMessage(index, element.extractIdOrNull(), th))
        }
    }

    return MessageBatch(messages, skipped)
}

/** Best-effort row id from a (possibly unparseable) element, never throws. */
private fun JsonElement.extractIdOrNull(): String? = try {
    if (isJsonObject) {
        asJsonObject.get("id")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
    } else {
        null
    }
} catch (th: Throwable) {
    null
}
