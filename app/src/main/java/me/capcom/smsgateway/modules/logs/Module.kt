package me.capcom.smsgateway.modules.logs

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val logsModule = module {
    singleOf(::LogsService)
}

val NAME = "logs"