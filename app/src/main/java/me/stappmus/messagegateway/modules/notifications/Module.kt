package me.stappmus.messagegateway.modules.notifications

import org.koin.dsl.module

val notificationsModule = module {
    single { NotificationsService(get()) }
}