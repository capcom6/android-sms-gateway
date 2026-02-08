package me.stappmus.messagegateway.helpers

import me.stappmus.messagegateway.BuildConfig

object BuildHelper {
    val isInsecureVersion =
        BuildConfig.BUILD_TYPE == "insecure" || BuildConfig.BUILD_TYPE == "debugInsecure"
}