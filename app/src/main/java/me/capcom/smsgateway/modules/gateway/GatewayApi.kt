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
import io.ktor.util.encodeBase64
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

    suspend fun devicePatch(token: String, request: DevicePatchRequest) {
        client.patch("$baseUrl/device") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getMessages(token: String): List<Message> {
        return client.get("$baseUrl/message") {
            bearerAuth(token)
        }.body()
    }

    suspend fun patchMessages(token: String, request: List<MessagePatchRequest>) {
        client.patch("$baseUrl/message") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getWebHooks(token: String): List<WebHook> {
        return client.get("$baseUrl/webhooks") {
            bearerAuth(token)
        }.body()
    }

    suspend fun changePassword(token: String, request: PasswordChangeRequest) {
        client.patch("$baseUrl/user/password") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private fun HttpRequestBuilder.bearerAuth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun HttpRequestBuilder.basicAuth(username: String, password: String) {
        header(HttpHeaders.Authorization, "Basic ${"$username:$password".encodeBase64()}")
    }

    data class DeviceGetResponse(
        val externalIp: String,
    )

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
        val state: ProcessingState,
        val recipients: List<RecipientState>,
        val states: Map<ProcessingState, Date>
    )

    data class PasswordChangeRequest(
        val currentPassword: String,
        val newPassword: String
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
        val state: ProcessingState,
        val error: String?,
    )

    data class WebHook(
        val id: String,
        val url: String,
        val event: WebHookEvent,
    )
}