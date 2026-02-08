package me.stappmus.messagegateway.modules.logs

import me.stappmus.messagegateway.modules.logs.repositories.LogsRepository
import me.stappmus.messagegateway.modules.logs.vm.LogsViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val logsModule = module {
    singleOf(::LogsRepository)
    singleOf(::LogsService)
    viewModelOf(::LogsViewModel)
}

val NAME = "logs"