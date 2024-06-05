package me.capcom.smsgateway.modules.webhooks

import org.koin.dsl.module

val webhooksModule = module {
    single { WebHooksService(get()) }
}