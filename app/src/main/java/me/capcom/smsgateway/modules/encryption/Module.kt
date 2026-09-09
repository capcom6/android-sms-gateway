package me.capcom.smsgateway.modules.encryption

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val encryptionModule = module {
    singleOf(::EncryptionService)
}