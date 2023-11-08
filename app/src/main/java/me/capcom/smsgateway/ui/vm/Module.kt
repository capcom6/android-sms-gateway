package me.capcom.smsgateway.ui.vm

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val vmModule = module {
    viewModel { MessagesListViewModel(get()) }
}