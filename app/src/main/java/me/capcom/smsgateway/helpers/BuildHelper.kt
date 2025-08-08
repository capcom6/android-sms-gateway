package me.capcom.smsgateway.helpers

import me.capcom.smsgateway.BuildConfig

object BuildHelper {
    val isInsecureVersion =
        BuildConfig.BUILD_TYPE == "insecure" || BuildConfig.BUILD_TYPE == "debugInsecure"
}