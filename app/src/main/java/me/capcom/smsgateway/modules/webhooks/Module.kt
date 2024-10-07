package me.capcom.smsgateway.modules.webhooks

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val webhooksModule = module {
    singleOf(::WebHooksService)
}

val NAME = "webhooks"