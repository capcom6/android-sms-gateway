package me.capcom.smsgateway.data

import org.koin.dsl.module

val dbModule = module {
    single { AppDatabase.getDatabase(get()) }
    single { get<AppDatabase>().messageDao() }
}