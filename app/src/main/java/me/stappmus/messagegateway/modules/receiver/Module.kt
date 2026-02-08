package me.stappmus.messagegateway.modules.receiver

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val receiverModule = module {
    singleOf(::ReceiverService)
}

val MODULE_NAME = "receiver"
