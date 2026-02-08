package me.stappmus.messagegateway.modules.webhooks

import me.stappmus.messagegateway.modules.webhooks.db.WebhookQueueRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val webhooksModule = module {
    singleOf(::WebHooksService)
    singleOf(::WebhookQueueRepository)
}

val NAME = "webhooks"