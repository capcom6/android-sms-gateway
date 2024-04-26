package me.capcom.smsgateway.modules.gateway

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.hostWithPort
import io.ktor.serialization.gson.gson
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.domain.MessageState
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
            gson()
        }
        expectSuccess = true
    }

    suspend fun deviceRegister(
        request: DeviceRegisterRequest
    ): DeviceRegisterResponse {
        return client.post("$baseUrl/device") {
            privateToken?.let { auth(it) }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun devicePatch(token: String, request: DevicePatchRequest) {
        client.patch("$baseUrl/device") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getMessages(token: String): List<Message> {
        return client.get("$baseUrl/message") {
            auth(token)
        }.body()
    }

    suspend fun patchMessages(token: String, request: List<MessagePatchRequest>) {
        client.patch("$baseUrl/message") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private fun HttpRequestBuilder.auth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    data class DeviceRegisterRequest(
        val name: String,
        val pushToken: String?,
    )

    data class DeviceRegisterResponse(
        val id: String,
        val token: String,
        val login: String,
        val password: String,
    )

    data class DevicePatchRequest(
        val id: String,
        val pushToken: String,
    )

    data class MessagePatchRequest(
        val id: String,
        val state: MessageState,
        val recipients: List<RecipientState>,
        val states: Map<MessageState, Long>
    )

    data class Message(
        val id: String,
        val message: String,
        val phoneNumbers: List<String>,
        val simNumber: Int?,
        val withDeliveryReport: Boolean?,
        val isEncrypted: Boolean?,
        val validUntil: Date?
    )

    data class RecipientState(
        val phoneNumber: String,
        val state: MessageState,
        val error: String?,
    )
}