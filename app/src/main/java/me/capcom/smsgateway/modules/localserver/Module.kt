package me.capcom.smsgateway.modules.localserver

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val localserverService = module {
    singleOf(::LocalServerSettings)
}