package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.modules.messages.repositories.MessagesRepository
import me.capcom.smsgateway.modules.messages.vm.MessagesListViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val messagesModule = module {
    single { MessagesRepository(get()) }
    single { MessagesService(get(), get()) }
    viewModel { MessagesListViewModel(get()) }
}