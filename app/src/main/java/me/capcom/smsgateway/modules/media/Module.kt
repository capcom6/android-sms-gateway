package me.capcom.smsgateway.modules.media

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val mediaModule = module {
    singleOf(::LocalMediaStorage) bind MediaStorage::class
    singleOf(::MediaService)
}
