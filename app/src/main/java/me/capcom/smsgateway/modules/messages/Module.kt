package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.modules.messages.repositories.MessagesRepository
import org.koin.dsl.module

val messagesModule = module {
    single { MessagesRepository(get()) }
    single { MessagesService(get(), get()) }
}