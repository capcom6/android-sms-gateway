package me.capcom.smsgateway.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import me.capcom.smsgateway.data.dao.ServerSettingsDao
import me.capcom.smsgateway.data.remote.dto.*
import me.capcom.smsgateway.modules.localsms.utils.Logger // Assuming Logger is accessible

class KtorServerClient(
    private val httpClient: HttpClient,
    private val serverSettingsDao: ServerSettingsDao
) {
    private val logger = Logger.get(this.javaClass.simpleName)

    private suspend inline fun <T> makeRequest(
        path: String,
        method: HttpMethod,
        crossinline bodyProvider: (HttpRequestBuilder.() -> Unit)? = null,
        requiresAuth: Boolean = true
    ): T? {
        val settings = serverSettingsDao.getSettingsDirect()
        if (settings == null || settings.serverUrl.isBlank()) {
            logger.error("Server URL not configured.")
            return null
        }

        val apiKey = settings.apiKey
        if (requiresAuth && (apiKey == null || apiKey.isBlank())) {
            logger.error("API Key not configured for authenticated request to $path.")
            return null
        }

        val url = "${settings.serverUrl.removeSuffix("/")}$path"

        return try {
            val response = httpClient.request(url) {
                this.method = method
                if (requiresAuth && apiKey != null) {
                    header("X-Agent-API-Key", apiKey)
                }
                bodyProvider?.invoke(this)
            }

            if (response.status.isSuccess()) {
                response.body<T>()
            } else {
                val errorBody = try { response.body<String>() } catch (e: Exception) { "N/A" }
                logger.error("Request to $url failed with status ${response.status}. Body: $errorBody")
                null
            }
        } catch (e: Exception) {
            logger.error("Exception during request to $url: ${e.message}", e)
            null
        }
    }

    suspend fun registerAgent(name: String): RegisterAgentResponse? {
        return makeRequest(
            path = "/agent/v1/register",
            method = HttpMethod.Post,
            bodyProvider = { setBody(RegisterAgentRequest(name)) },
            requiresAuth = false // Registration does not require existing API key
        )
    }

    suspend fun sendHeartbeat(): Boolean {
        return makeRequest<String>( // Expecting a simple OK response, body might not be parsed or be Unit
            path = "/agent/v1/heartbeat",
            method = HttpMethod.Post
        ) != null // Success if request didn't return null (i.e., was successful)
    }

    suspend fun fetchAgentConfig(): AgentConfigResponse? {
        return makeRequest(
            path = "/agent/v1/config",
            method = HttpMethod.Get
        )
    }

    suspend fun fetchOutgoingSmsTasks(): List<AgentSmsTaskResponse>? {
        return makeRequest(
            path = "/agent/v1/tasks/sms/outgoing",
            method = HttpMethod.Get
        )
    }

    suspend fun updateTaskStatus(taskId: String, statusUpdateRequest: TaskStatusUpdateRequest): Boolean {
        return makeRequest<String>( // Expecting a simple OK response
            path = "/agent/v1/tasks/sms/outgoing/$taskId/status",
            method = HttpMethod.Post,
            bodyProvider = { setBody(statusUpdateRequest) }
        ) != null
    }

    suspend fun reportIncomingSms(incomingSmsRequest: IncomingSmsRequest): Boolean {
        return makeRequest<String>( // Expecting a simple OK response
            path = "/agent/v1/messages/incoming",
            method = HttpMethod.Post,
            bodyProvider = { setBody(incomingSmsRequest) }
        ) != null
    }
}
