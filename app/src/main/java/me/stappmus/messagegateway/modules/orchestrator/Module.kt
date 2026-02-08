package me.stappmus.messagegateway.modules.orchestrator

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val orchestratorModule = module {
    singleOf(::OrchestratorService)
    singleOf(::EventsRouter)
}

val MODULE_NAME = "orchestrator"
