package me.stappmus.messagegateway.modules.messages

import me.stappmus.messagegateway.modules.messages.vm.MessageDetailsViewModel
import me.stappmus.messagegateway.modules.messages.vm.MessagesListViewModel
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
