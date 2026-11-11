package me.capcom.smsgateway.modules.messages.providers

import me.capcom.smsgateway.domain.MessageContent

class SendMessageProviderFactory(
    private val providers: List<SendMessageProvider>,
) {
    fun resolve(content: MessageContent): SendMessageProvider =
        providers.firstOrNull { it.supports(content) }
            ?: throw IllegalArgumentException("Unsupported message content: ${content::class.simpleName}")
}
