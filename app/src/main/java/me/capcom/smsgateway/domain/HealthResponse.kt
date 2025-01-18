package me.capcom.smsgateway.domain

import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.modules.health.domain.CheckResult
import me.capcom.smsgateway.modules.health.domain.HealthResult
import me.capcom.smsgateway.modules.health.domain.Status

class HealthResponse(
    healthResult: HealthResult,

    val version: String = BuildConfig.VERSION_NAME,
    val releaseId: Int = BuildConfig.VERSION_CODE,
) {
    val status: Status = healthResult.status
    val checks: Map<String, CheckResult> = healthResult.checks
}