package me.capcom.smsgateway.modules.localserver.auth

enum class AuthScopes(val value: String) {
    AllAny("all:any"),

    MessagesSend("messages:send"),
    MessagesRead("messages:read"),
    MessagesExport("messages:export"),

    DevicesList("devices:list"),

    WebhooksList("webhooks:list"),
    WebhooksWrite("webhooks:write"),
    WebhooksDelete("webhooks:delete"),

    SettingsRead("settings:read"),
    SettingsWrite("settings:write"),

    LogsRead("logs:read"),

    TokensManage("tokens:manage");

    companion object {
        private val supportedValues: Set<String> = values().mapTo(HashSet()) { it.value }
        fun firstUnsupported(scopes: Iterable<String>): String? {
            return scopes.firstOrNull { it !in supportedValues }
        }
    }
}
