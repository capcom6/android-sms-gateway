package me.capcom.smsgateway.modules.mms

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val mmsModule = module {
    single { MmsAttachmentStorage(androidContext()) }
}
