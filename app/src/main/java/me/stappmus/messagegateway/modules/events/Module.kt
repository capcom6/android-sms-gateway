package me.stappmus.messagegateway.modules.events

import org.koin.dsl.module

val eventBusModule = module {
    single { EventBus() }
}