package me.capcom.smsgateway.modules.device

import me.capcom.smsgateway.modules.device.keys.KeyStore
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val deviceModule = module {
    singleOf(::KeyStore)
    singleOf(::DeviceService)
}

internal const val MODULE_NAME = "device"