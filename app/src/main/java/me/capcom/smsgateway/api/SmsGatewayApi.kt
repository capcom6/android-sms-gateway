package me.capcom.smsgateway.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import me.capcom.smsgateway.domain.MessageState

class SmsGatewayApi {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            gson()
        }
        expectSuccess = true
    }

    suspend fun register(request: RegisterRequest): RegisterResponse {
        return client.post("$BASE_URL/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getMessages(): List<Message> {
        return client.get("$BASE_URL/message").body()
    }

    suspend fun patchMessages(request: MessagePatchRequest) {
        client.patch("$BASE_URL/message") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    data class RegisterRequest(
        val name: String,
        val pushToken: String?,
    )

    data class RegisterResponse(
        val id: String,
        val token: String,
        val login: String,
        val password: String,
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