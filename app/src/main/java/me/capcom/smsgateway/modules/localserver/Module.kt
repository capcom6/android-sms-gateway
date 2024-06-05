package me.capcom.smsgateway.modules.localserver

import org.koin.dsl.module

val localserverService = module {
    single { LocalServerService(get()) }
}