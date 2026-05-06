package me.capcom.smsgateway.modules.localserver.auth

enum class AuthScopes(val value: String) {
    AllAny("all:any"),

    MessagesSend("messages:send"),
    MessagesRead("messages:read"),
    MessagesExport("messages:export"),

    InboxList("inbox:list"),
    InboxRead("inbox:read"),
    InboxRefresh("inbox:refresh"),

    DevicesList("devices:list"),

    WebhooksList("webhooks:list"),
    WebhooksWrite("webhooks:write"),
    WebhooksDelete("webhooks:delete"),

    SettingsRead("settings:read"),
    SettingsWrite("settings:write"),

    LogsRead("logs:read"),

    TokensManage("tokens:manage"),
    TokensRefresh("tokens:refresh");

    companion object {
        private val supportedValues: Set<String> = values().mapTo(HashSet()) { it.value }
        fun firstUnsupported(scopes: Iterable<String>): String? {
            // TokensRefresh is a system-only scope: clients must not request it on access tokens;
            // it is issued by the server on the refresh-token component of a generated pair.
            return scopes.firstOrNull { it !in supportedValues || it == TokensRefresh.value }
        }
    }
}
