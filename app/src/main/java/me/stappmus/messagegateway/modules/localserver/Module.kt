package me.stappmus.messagegateway.modules.localserver

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val localserverModule = module {
    singleOf(::LocalServerService)
}