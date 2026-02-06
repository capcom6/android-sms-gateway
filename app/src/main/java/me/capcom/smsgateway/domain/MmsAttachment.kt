package me.capcom.smsgateway.domain

class MmsAttachment(
    val id: String,
    val mimeType: String,
    val filename: String?,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val sha256: String? = null,
    val downloadUrl: String? = null,
)
