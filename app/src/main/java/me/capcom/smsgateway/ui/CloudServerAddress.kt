package me.capcom.smsgateway.ui

import io.ktor.http.Url
import io.ktor.http.hostWithPort

internal fun cloudServerAddress(serverUrl: String): String? {
    if (serverUrl.isBlank()) return null
    return try {
        val url = Url(serverUrl)
        url.hostWithPort.takeIf { url.host.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
