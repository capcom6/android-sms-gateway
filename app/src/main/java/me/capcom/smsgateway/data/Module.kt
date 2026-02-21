package me.capcom.smsgateway.data

import org.koin.dsl.module

val dbModule = module {
    single { AppDatabase.getDatabase(get()) }
    single { get<AppDatabase>().messagesDao() }
    single { get<AppDatabase>().webhooksDao() }
    single { get<AppDatabase>().webhookQueueDao() }
    single { get<AppDatabase>().logDao() }
    single { get<AppDatabase>().revokedTokensDao() }
}