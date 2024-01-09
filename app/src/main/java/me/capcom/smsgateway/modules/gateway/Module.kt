package me.capcom.smsgateway.modules.gateway

import org.koin.dsl.module

val gatewayModule = module {
    single { GatewayModule(get(), get()) }
}