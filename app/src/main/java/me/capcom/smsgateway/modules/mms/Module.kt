package me.capcom.smsgateway.modules.mms

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mmsModule = module {
    singleOf(::MmsAttachmentStorage)
}

val MODULE_NAME = "mms"