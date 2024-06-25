package me.capcom.smsgateway.modules.gateway

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val gatewayService = module {
    singleOf(::GatewayService)
}