package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.modules.messages.repositories.MessagesRepository
import me.capcom.smsgateway.modules.messages.vm.MessageDetailsViewModel
import me.capcom.smsgateway.modules.messages.vm.MessagesListViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val messagesModule = module {
    single { MessagesRepository(get()) }
    singleOf(::MessagesService)
    viewModel { MessagesListViewModel(get()) }
    viewModel { MessageDetailsViewModel(get()) }
}

val MODULE_NAME = "messages"
