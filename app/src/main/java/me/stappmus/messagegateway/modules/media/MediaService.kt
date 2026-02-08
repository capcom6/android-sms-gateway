package me.stappmus.messagegateway.modules.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import me.stappmus.messagegateway.domain.MmsAttachment
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MediaService(
    private val storage: MediaStorage,
    private val settings: MediaSettings,
) {
    private var lastCleanupAt: Long = 0

    data class MediaDownload(
        val mimeType: String,
        val filename: String?,
        val bytes: ByteArray,
    )

    fun storeOutgoingAttachment(
        bytes: ByteArray,
        originalFilename: String?,
        mimeType: String,
        id: String? = null,
        width: Int? = null,
        height: Int? = null,
        durationMs: Long? = null,
        sha256: String? = null,
    ): MmsAttachment {
        enforceSizeLimit(bytes.size.toLong())

        val computedMetadata = resolveMediaMetadata(bytes, mimeType)
        val resolvedSha = sha256 ?: sha256(bytes)

        val stored = storage.store(
            StoreMediaRequest(
                id = id,
                bytes = bytes,
                mimeType = mimeType,
                filename = originalFilename,
                size = bytes.size.toLong(),
                width = width ?: computedMetadata.width,
                height = height ?: computedMetadata.height,
                durationMs = durationMs ?: computedMetadata.durationMs,
                sha256 = resolvedSha,
            )
        )

        maybeCleanup()

        return MmsAttachment(
            id = stored.id,
            mimeType = stored.mimeType,
            filename = stored.filename,
            size = stored.size,
            width = stored.width,
            height = stored.height,
            durationMs = stored.durationMs,
            sha256 = stored.sha256,
            downloadUrl = buildSignedDownloadUrl(stored.id),
        )
    }

    fun cacheIncomingAttachments(context: Context, attachments: List<MmsAttachment>): List<MmsAttachment> {
        return attachments.map { cacheIncomingAttachment(context, it) }
    }

    fun cacheIncomingAttachment(context: Context, attachment: MmsAttachment): MmsAttachment {
        val source = attachment.downloadUrl
        if (source.isNullOrBlank() || !source.startsWith("content://")) {
            return attachment
        }

        return try {
            val bytes = context.contentResolver.openInputStream(Uri.parse(source))
                ?.use { it.readBytes() }
                ?: return attachment

            storeOutgoingAttachment(
                bytes = bytes,
                originalFilename = attachment.filename,
                mimeType = attachment.mimeType,
                id = attachment.id,
                width = attachment.width,
                height = attachment.height,
                durationMs = attachment.durationMs,
                sha256 = attachment.sha256,
            )
        } catch (_: Exception) {
            attachment
        }
    }

    fun resolveOutgoingAttachmentBytes(context: Context, attachment: MmsAttachment): ByteArray? {
        storage.readBytes(attachment.id)?.let {
            return it
        }

        val source = attachment.downloadUrl ?: return null
        return try {
            when {
                source.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes() }
                }

                source.startsWith("file://") -> {
                    val path = Uri.parse(source).path ?: return null
                    val file = File(path)
                    if (!file.exists()) null else file.readBytes()
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun resolveDownload(id: String, expires: Long?, token: String?): MediaDownload? {
        if (!isTokenValid(id, expires, token)) {
            return null
        }

        val stored = storage.get(id) ?: return null
        val bytes = storage.readBytes(id) ?: return null

        return MediaDownload(
            mimeType = stored.mimeType,
            filename = stored.filename,
            bytes = bytes,
        )
    }

    private fun buildSignedDownloadUrl(mediaId: String): String {
        val expires = System.currentTimeMillis() + (settings.tokenTtlSeconds * 1000L)
        val token = sign(mediaId, expires)

        return "/media/$mediaId?expires=$expires&token=$token"
    }

    private fun isTokenValid(mediaId: String, expires: Long?, token: String?): Boolean {
        if (expires == null || token.isNullOrBlank()) {
            return false
        }

        if (expires < System.currentTimeMillis()) {
            return false
        }

        val expected = sign(mediaId, expires)
        return MessageDigest.isEqual(
            token.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }

    private fun sign(mediaId: String, expires: Long): String {
        val payload = "$mediaId:$expires"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(settings.signingKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))

        return Base64.encodeToString(
            mac.doFinal(payload.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
        )
    }

    private fun enforceSizeLimit(size: Long) {
        val maxSize = settings.maxAttachmentSizeMb * 1024L * 1024L
        if (size > maxSize) {
            throw IllegalArgumentException("Attachment size exceeds ${settings.maxAttachmentSizeMb}MB")
        }
    }

    private fun maybeCleanup() {
        val now = System.currentTimeMillis()
        if (now - lastCleanupAt < CLEANUP_INTERVAL_MS) {
            return
        }

        storage.cleanup(settings.retentionDays)
        lastCleanupAt = now
    }

    private fun resolveMediaMetadata(bytes: ByteArray, mimeType: String): MediaMetadata {
        val normalized = mimeType.lowercase()

        return when {
            normalized.startsWith("image/") -> {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                MediaMetadata(
                    width = options.outWidth.takeIf { it > 0 },
                    height = options.outHeight.takeIf { it > 0 },
                    durationMs = null,
                )
            }

            normalized.startsWith("video/") || normalized.startsWith("audio/") -> {
                val tempFile = File.createTempFile("media-metadata", ".tmp")
                tempFile.writeBytes(bytes)

                val retriever = MediaMetadataRetriever()
                val metadata = try {
                    retriever.setDataSource(tempFile.absolutePath)
                    MediaMetadata(
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            ?.toIntOrNull(),
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            ?.toIntOrNull(),
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull(),
                    )
                } catch (_: Exception) {
                    MediaMetadata(null, null, null)
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                    tempFile.delete()
                }

                metadata
            }

            else -> MediaMetadata(null, null, null)
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private data class MediaMetadata(
        val width: Int?,
        val height: Int?,
        val durationMs: Long?,
    )

    companion object {
        private const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L
    }
}
