package me.capcom.smsgateway.modules.webhooks

import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val webhooksModule = module {
    singleOf(::WebHooksService)
    singleOf(::WebhookQueueRepository)
}

val NAME = "webhooks"