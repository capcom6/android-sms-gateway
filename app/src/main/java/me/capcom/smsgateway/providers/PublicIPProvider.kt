package me.capcom.smsgateway.providers

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.gson.*

class PublicIPProvider: IPProvider {
    override suspend fun getIP(): String? {
        return try {
            client.get("https://api.ipify.org/?format=json").body<IpifyResponse>().ip
        } catch (e: Exception) {
            null
        }
    }

    private data class IpifyResponse(val ip: String)

    companion object {
        private val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                gson()
            }
        }
    }
}