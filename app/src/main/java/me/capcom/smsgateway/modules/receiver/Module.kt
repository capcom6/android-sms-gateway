package me.capcom.smsgateway.modules.receiver

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val receiverModule = module {
    singleOf(::ReceiverService)
}