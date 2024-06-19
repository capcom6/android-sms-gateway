package me.capcom.smsgateway.modules.gateway

import org.koin.dsl.module

val gatewayService = module {
    single { GatewayService(get(), get()) }
}