package me.capcom.smsgateway.modules.encryption

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val encryptionModule = module {
    single {
        EncryptionService(get(), get())
    }
    singleOf(::E2EKeyService)
}

val MODULE_NAME = "Encryption"