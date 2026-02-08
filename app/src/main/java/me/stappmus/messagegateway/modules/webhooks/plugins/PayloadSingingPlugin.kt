package me.stappmus.messagegateway.modules.webhooks.plugins

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

val PayloadSingingPlugin =
    createClientPlugin("PayloadSingingPlugin", ::PayloadSingingPluginConfig) {
        val algorithm = pluginConfig.hmacAlgorithm
        val headerName = pluginConfig.signatureHeaderName
        val timestampHeaderName = pluginConfig.timestampHeaderName
        val secretKeyProvider = pluginConfig.secretKeyProvider

        transformRequestBody { request, content, bodyType ->
            if (content !is OutgoingContent) {
                return@transformRequestBody null
            }

            if (content is TextContent) {
                val secretKey = secretKeyProvider() ?: return@transformRequestBody content

                val timestamp = (System.currentTimeMillis() / 1000).toString()
                val message = content.text + timestamp

                request.header(timestampHeaderName, timestamp)
                request.header(
                    headerName, generateSignature(
                        algorithm,
                        secretKey,
                        message
                    )
                )
            }

            content
        }
    }

class PayloadSingingPluginConfig {
    var secretKeyProvider: () -> String? = { null }
    var signatureHeaderName: String = "X-Signature"
    var timestampHeaderName: String = "X-Timestamp"
    var hmacAlgorithm: String = "HmacSHA256"
}

fun generateSignature(algorithm: String, secretKey: String, payload: String): String {
    val mac = Mac.getInstance(algorithm)
    val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), algorithm)
    mac.init(secretKeySpec)
    val hash = mac.doFinal(payload.toByteArray())

    return hash.toHexString()
}

private fun ByteArray.toHexString(): String {
    return joinToString("") { "%02x".format(it) }
}