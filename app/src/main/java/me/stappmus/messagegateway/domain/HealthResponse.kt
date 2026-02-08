package me.stappmus.messagegateway.domain

import me.stappmus.messagegateway.BuildConfig
import me.stappmus.messagegateway.modules.health.domain.CheckResult
import me.stappmus.messagegateway.modules.health.domain.HealthResult
import me.stappmus.messagegateway.modules.health.domain.Status

class HealthResponse(
    healthResult: HealthResult,

    val version: String = BuildConfig.VERSION_NAME,
    val releaseId: Int = BuildConfig.VERSION_CODE,
) {
    val status: Status = healthResult.status
    val checks: Map<String, CheckResult> = healthResult.checks
}