package me.capcom.smsgateway.modules.health.domain

data class CheckResult(
    val status: Status,
    val observedValue: Long,
    val observedUnit: String,
    val description: String,
)
