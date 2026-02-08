package me.stappmus.messagegateway.modules.media

data class StoreMediaRequest(
    val id: String?,
    val bytes: ByteArray,
    val mimeType: String,
    val filename: String?,
    val size: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val sha256: String?,
)

data class StoredMedia(
    val id: String,
    val mimeType: String,
    val filename: String?,
    val size: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val sha256: String?,
    val createdAt: Long,
    val blobFile: String,
)

interface MediaStorage {
    fun store(request: StoreMediaRequest): StoredMedia
    fun get(id: String): StoredMedia?
    fun readBytes(id: String): ByteArray?
    fun cleanup(retentionDays: Int): Int
}
