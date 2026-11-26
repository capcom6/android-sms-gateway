package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.modules.messages.providers.DataSendProvider
import me.capcom.smsgateway.modules.messages.providers.MmsSendProvider
import me.capcom.smsgateway.modules.messages.providers.PhoneNormalizer
import me.capcom.smsgateway.modules.messages.providers.SendMessageProviderFactory
import me.capcom.smsgateway.modules.messages.providers.SimSelector
import me.capcom.smsgateway.modules.messages.providers.TextSendProvider
import me.capcom.smsgateway.modules.messages.vm.MessageDetailsViewModel
import me.capcom.smsgateway.modules.messages.vm.MessagesListViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val messagesModule = module {
    singleOf(::MessagesRepository)
    singleOf(::MessagesService)
    singleOf(::MessageStateService)
    single { SimSelector(get(), get()) }
    single { PhoneNormalizer(get(), get()) }
    singleOf(::TextSendProvider)
    singleOf(::DataSendProvider)
    singleOf(::MmsSendProvider)
    single {
        SendMessageProviderFactory(
            listOf(get<TextSendProvider>(), get<DataSendProvider>(), get<MmsSendProvider>())
        )
    }
    viewModel { MessagesListViewModel(get()) }
    viewModel { MessageDetailsViewModel(get()) }
}

val MODULE_NAME = "messages"
