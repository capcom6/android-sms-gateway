package me.capcom.smsgateway.modules.mms

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mmsModule = module {
    singleOf(::MmsAttachmentStorage)
    singleOf(::MmsSender)
}

internal const val MODULE_NAME = "mms"