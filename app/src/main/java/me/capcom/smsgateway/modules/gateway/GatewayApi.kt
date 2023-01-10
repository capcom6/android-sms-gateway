package me.capcom.smsgateway.modules.gateway

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import me.capcom.smsgateway.domain.MessageState

class GatewayApi() {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            gson()
        }
        expectSuccess = true
    }

    suspend fun deviceRegister(request: DeviceRegisterRequest): DeviceRegisterResponse {
        return client.post("$BASE_URL/device") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun devicePatch(token: String, request: DevicePatchRequest) {
        client.patch("$BASE_URL/device") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getMessages(token: String): List<Message> {
        return client.get("$BASE_URL/message") {
            auth(token)
        }.body()
    }

    suspend fun patchMessages(token: String, request: List<MessagePatchRequest>) {
        client.patch("$BASE_URL/message") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    fun HttpRequestBuilder.auth(token: String) {
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
    )

    data class Message(
        val id: String,
        val message: String,
        val phoneNumbers: List<String>
    )

    data class RecipientState(
        val phoneNumber: String,
        val state: MessageState,
    )

    companion object {
        private const val BASE_URL = "https://sms.capcom.me/api/mobile/v1"
    }
}