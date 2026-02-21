package me.capcom.smsgateway.modules.localserver.auth

object AuthScopes {
    const val ALL_ANY = "all:any"

    const val MESSAGES_SEND = "messages:send"
    const val MESSAGES_READ = "messages:read"
    const val DEVICES_LIST = "devices:list"
    const val DEVICES_WRITE = "devices:write"
    const val WEBHOOKS_LIST = "webhooks:list"
    const val WEBHOOKS_WRITE = "webhooks:write"
    const val SETTINGS_READ = "settings:read"
    const val SETTINGS_WRITE = "settings:write"
    const val LOGS_READ = "logs:read"

    val allowed = setOf(
        ALL_ANY,
        MESSAGES_SEND,
        MESSAGES_READ,
        DEVICES_LIST,
        DEVICES_WRITE,
        WEBHOOKS_LIST,
        WEBHOOKS_WRITE,
        SETTINGS_READ,
        SETTINGS_WRITE,
        LOGS_READ,
    )

    fun parseCsv(value: String): List<String> {
        return value.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun firstUnsupported(scopes: List<String>): String? {
        return scopes.firstOrNull { it !in allowed }
    }
}

