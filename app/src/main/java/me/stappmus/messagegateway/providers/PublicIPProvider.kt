package me.stappmus.messagegateway.providers

import java.net.URL

class PublicIPProvider: IPProvider {
    override suspend fun getIP(): String? {
        return try {
            URL("https://api.ipify.org").readText().trim()
        } catch (e: Exception) {
            try {
                URL("https://checkip.amazonaws.com").readText().trim()
            } catch (e2: Exception) {
                e2.printStackTrace()
                null
            }
        }
    }
}