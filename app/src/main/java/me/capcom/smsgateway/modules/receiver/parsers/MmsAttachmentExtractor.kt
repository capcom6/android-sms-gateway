package me.capcom.smsgateway.modules.receiver.parsers

import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import me.capcom.smsgateway.domain.MmsAttachment
import java.security.MessageDigest

object MmsAttachmentExtractor {
    private val mmsContentUri = Uri.parse("content://mms")
    private val mmsPartContentUri = Uri.parse("content://mms/part")

    private const val COLUMN_ID = "_id"
    private const val COLUMN_MESSAGE_ID = "m_id"
    private const val COLUMN_TRANSACTION_ID = "tr_id"
    private const val COLUMN_MIME_TYPE = "ct"
    private const val COLUMN_FILE_NAME = "fn"
    private const val COLUMN_NAME = "name"
    private const val COLUMN_CONTENT_LOCATION = "cl"
    private const val COLUMN_TEXT = "text"

    private const val MIME_TEXT = "text/plain"
    private const val MIME_SMIL = "application/smil"
    private const val HASH_BUFFER_SIZE = 8192

    fun extract(context: Context, messageId: String?, transactionId: String): List<MmsAttachment> {
        val mmsRowId = resolveMmsRowId(context, messageId, transactionId) ?: return emptyList()

        val projection = arrayOf(
            COLUMN_ID,
            COLUMN_MIME_TYPE,
            COLUMN_FILE_NAME,
            COLUMN_NAME,
            COLUMN_CONTENT_LOCATION,
            COLUMN_TEXT,
        )

        return context.contentResolver.query(
            mmsPartContentUri,
            projection,
            "mid = ?",
            arrayOf(mmsRowId.toString()),
            "seq ASC"
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    try {
                        val mimeType = cursor.getString(COLUMN_MIME_TYPE)
                        if (!isAttachmentMimeType(mimeType)) {
                            continue
                        }

                        val partId = cursor.getString(COLUMN_ID) ?: continue
                        val partUri = Uri.withAppendedPath(mmsPartContentUri, partId)
                        val textValue = cursor.getString(COLUMN_TEXT)
                        val size = resolveSize(context, partUri, textValue)
                        val media = resolveMediaMetadata(context, partUri, mimeType)

                        add(
                            MmsAttachment(
                                id = partId,
                                mimeType = mimeType ?: "application/octet-stream",
                                filename = pickFileName(
                                    cursor.getString(COLUMN_FILE_NAME),
                                    cursor.getString(COLUMN_NAME),
                                    cursor.getString(COLUMN_CONTENT_LOCATION),
                                ),
                                size = size,
                                width = media.width,
                                height = media.height,
                                durationMs = media.durationMs,
                                sha256 = resolveSha256(context, partUri),
                                downloadUrl = partUri.toString(),
                            )
                        )
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        } ?: emptyList()
    }

    internal fun isAttachmentMimeType(mimeType: String?): Boolean {
        val value = mimeType?.trim()?.lowercase() ?: return false
        if (value.isBlank()) {
            return false
        }
        return value != MIME_TEXT && value != MIME_SMIL
    }

    internal fun normalizeTokenCandidates(value: String?): List<String> {
        val source = value?.trim().orEmpty()
        if (source.isEmpty()) {
            return emptyList()
        }

        val withoutNullTerminator = source.removeSuffix("\u0000")
        val withoutAngleBrackets = withoutNullTerminator.trim('<', '>')

        return listOf(
            source,
            withoutNullTerminator,
            withoutAngleBrackets,
        )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun resolveMmsRowId(context: Context, messageId: String?, transactionId: String): Long? {
        for (token in normalizeTokenCandidates(messageId)) {
            queryMmsRowId(context, "$COLUMN_MESSAGE_ID = ?", arrayOf(token))?.let { return it }
        }

        for (token in normalizeTokenCandidates(transactionId)) {
            queryMmsRowId(context, "$COLUMN_TRANSACTION_ID = ?", arrayOf(token))?.let { return it }
        }

        for (token in normalizeTokenCandidates(transactionId)) {
            queryMmsRowId(context, "$COLUMN_TRANSACTION_ID LIKE ?", arrayOf("%$token%"))?.let {
                return it
            }
        }

        return null
    }

    private fun queryMmsRowId(context: Context, selection: String, selectionArgs: Array<String>): Long? {
        return try {
            context.contentResolver.query(
                mmsContentUri,
                arrayOf(COLUMN_ID),
                selection,
                selectionArgs,
                "date DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pickFileName(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun resolveSize(context: Context, uri: Uri, text: String?): Long {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                if (it.length >= 0) {
                    return it.length
                }
            }
        } catch (_: Exception) {
        }

        if (!text.isNullOrEmpty()) {
            return text.toByteArray(Charsets.UTF_8).size.toLong()
        }

        try {
            context.contentResolver.openInputStream(uri)?.use {
                return it.available().toLong()
            }
        } catch (_: Exception) {
        }

        return 0L
    }

    private fun resolveSha256(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = ByteArray(HASH_BUFFER_SIZE)

            context.contentResolver.openInputStream(uri)?.use { input ->
                while (true) {
                    val count = input.read(bytes)
                    if (count < 0) {
                        break
                    }
                    digest.update(bytes, 0, count)
                }
            } ?: return null

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveMediaMetadata(context: Context, uri: Uri, mimeType: String?): MediaMetadata {
        val value = mimeType?.lowercase().orEmpty()

        return when {
            value.startsWith("image/") -> resolveImageMetadata(context, uri)
            value.startsWith("video/") || value.startsWith("audio/") -> resolveRetrieverMetadata(
                context,
                uri,
                isVideo = value.startsWith("video/")
            )

            else -> MediaMetadata(null, null, null)
        }
    }

    private fun resolveImageMetadata(context: Context, uri: Uri): MediaMetadata {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val width = options.outWidth.takeIf { it > 0 }
            val height = options.outHeight.takeIf { it > 0 }
            MediaMetadata(width, height, null)
        } catch (_: Exception) {
            MediaMetadata(null, null, null)
        }
    }

    private fun resolveRetrieverMetadata(context: Context, uri: Uri, isVideo: Boolean): MediaMetadata {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(context, uri)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val width = if (isVideo) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
            } else {
                null
            }
            val height = if (isVideo) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
            } else {
                null
            }

            MediaMetadata(width, height, duration)
        } catch (_: Exception) {
            MediaMetadata(null, null, null)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private data class MediaMetadata(
        val width: Int?,
        val height: Int?,
        val durationMs: Long?,
    )

    private fun Cursor.getString(columnName: String): String? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) {
            return null
        }

        return getString(index)
    }
}
