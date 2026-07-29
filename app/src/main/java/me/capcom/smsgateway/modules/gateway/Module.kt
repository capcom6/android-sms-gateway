package me.capcom.smsgateway.modules.gateway

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val gatewayModule = module {
    singleOf(::GatewayService)
    factory {
        val settings: GatewaySettings = get()
        GatewayApi(settings.serverUrl, settings.privateToken)
    }
}

val MODULE_NAME = "gateway"
