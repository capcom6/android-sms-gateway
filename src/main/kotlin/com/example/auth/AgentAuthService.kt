package com.example.auth

import java.security.MessageDigest
import java.util.UUID

class AgentAuthService {
    fun generateApiKey(): String = UUID.randomUUID().toString().replace("-", "")

    fun hashApiKey(apiKey: String): String {
        val bytes = apiKey.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
