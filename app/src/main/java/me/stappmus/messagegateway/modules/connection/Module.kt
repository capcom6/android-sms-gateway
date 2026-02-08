package me.stappmus.messagegateway.modules.connection

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val connectionModule = module {
    singleOf(::ConnectionService)
}

val MODULE_NAME = "connection"