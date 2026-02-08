package me.stappmus.messagegateway.modules.receiver.parsers

import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import me.stappmus.messagegateway.domain.MmsAttachment
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
    private const val COLUMN_SUBJECT = "sub"
    private const val COLUMN_CONTENT_CLASS = "ct_cls"
    private const val COLUMN_MESSAGE_SIZE = "m_size"
    private const val COLUMN_DATE = "date"
    private const val COLUMN_MESSAGE_BOX = "msg_box"

    private const val MIME_TEXT = "text/plain"
    private const val MIME_SMIL = "application/smil"
    private const val HASH_BUFFER_SIZE = 8192
    private const val MESSAGE_BOX_INBOX = 1
    private const val RECENT_WINDOW_SECONDS = 5 * 60L
    private const val ADDRESS_TYPE_FROM = 137
    private val EXTRACTION_RETRY_DELAYS_MS = longArrayOf(0L, 1000L, 2000L, 4000L, 6000L, 8000L, 10000L, 12000L)

    data class ExtractionResult(
        val attachments: List<MmsAttachment>,
        val providerMessageId: String?,
        val subject: String?,
        val contentClass: String?,
        val size: Long?,
    )

    fun extract(
        context: Context,
        messageId: String?,
        transactionId: String,
        receivedAtEpochMs: Long,
        senderAddress: String?,
    ): ExtractionResult {
        val normalizedSender = normalizeAddress(senderAddress)
        Log.d(
            TAG,
            "extract start messageId=$messageId transactionId=$transactionId sender=$normalizedSender"
        )

        var lastRow: MmsRow? = null

        for ((attempt, delayMs) in EXTRACTION_RETRY_DELAYS_MS.withIndex()) {
            if (delayMs > 0) {
                Thread.sleep(delayMs)
            }

            val mmsRow = resolveMmsRow(
                context = context,
                messageId = messageId,
                transactionId = transactionId,
                receivedAtEpochMs = receivedAtEpochMs,
                senderAddress = normalizedSender,
            )

            if (mmsRow == null) {
                Log.d(TAG, "extract attempt=${attempt + 1}: no mms row")
                continue
            }

            lastRow = mmsRow
            val parts = extractParts(context, mmsRow.id)
            Log.d(
                TAG,
                "extract attempt=${attempt + 1}: rowId=${mmsRow.id}, parts=${parts.size}, " +
                        "subject=${mmsRow.subject}, contentClass=${mmsRow.contentClass}, size=${mmsRow.size}"
            )

            if (parts.isNotEmpty()) {
                return ExtractionResult(
                    attachments = parts,
                    providerMessageId = mmsRow.messageId?.takeIf { it.isNotBlank() } ?: messageId,
                    subject = mmsRow.subject?.takeIf { it.isNotBlank() },
                    contentClass = mmsRow.contentClass?.takeIf { it.isNotBlank() },
                    size = mmsRow.size,
                )
            }

            val fallbackRow = queryRecentInboxMmsRow(
                context = context,
                receivedAtEpochMs = receivedAtEpochMs,
                senderAddress = normalizedSender,
                excludeRowId = mmsRow.id,
            )

            if (fallbackRow != null) {
                val fallbackParts = extractParts(context, fallbackRow.id)
                Log.d(
                    TAG,
                    "extract attempt=${attempt + 1}: fallbackRowId=${fallbackRow.id}, parts=${fallbackParts.size}"
                )

                if (fallbackParts.isNotEmpty()) {
                    return ExtractionResult(
                        attachments = fallbackParts,
                        providerMessageId = fallbackRow.messageId?.takeIf { it.isNotBlank() } ?: messageId,
                        subject = fallbackRow.subject?.takeIf { it.isNotBlank() },
                        contentClass = fallbackRow.contentClass?.takeIf { it.isNotBlank() },
                        size = fallbackRow.size,
                    )
                }
            }
        }

        return ExtractionResult(
            attachments = emptyList(),
            providerMessageId = lastRow?.messageId?.takeIf { it.isNotBlank() } ?: messageId,
            subject = lastRow?.subject?.takeIf { it.isNotBlank() },
            contentClass = lastRow?.contentClass?.takeIf { it.isNotBlank() },
            size = lastRow?.size,
        )
    }

    fun extractFromRow(
        context: Context,
        rowId: Long,
        messageId: String?,
        transactionId: String,
        receivedAtEpochMs: Long,
        senderAddress: String?,
    ): ExtractionResult {
        val normalizedSender = normalizeAddress(senderAddress)
        val row = queryMmsRow(
            context,
            "$COLUMN_ID = ?",
            arrayOf(rowId.toString()),
        )

        if (row != null) {
            val parts = extractParts(context, row.id)
            if (parts.isNotEmpty()) {
                return ExtractionResult(
                    attachments = parts,
                    providerMessageId = row.messageId?.takeIf { it.isNotBlank() } ?: messageId,
                    subject = row.subject?.takeIf { it.isNotBlank() },
                    contentClass = row.contentClass?.takeIf { it.isNotBlank() },
                    size = row.size,
                )
            }
        }

        return extract(
            context = context,
            messageId = messageId,
            transactionId = transactionId,
            receivedAtEpochMs = receivedAtEpochMs,
            senderAddress = normalizedSender,
        )
    }

    private fun extractParts(context: Context, mmsRowId: Long): List<MmsAttachment> {

        val projection = arrayOf(
            COLUMN_ID,
            COLUMN_MIME_TYPE,
            COLUMN_FILE_NAME,
            COLUMN_NAME,
            COLUMN_CONTENT_LOCATION,
            COLUMN_TEXT,
        )

        val uri = Uri.parse("content://mms/$mmsRowId/part")

        return context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "seq ASC"
        )?.use { cursor ->
            Log.d(TAG, "extractParts rowId=$mmsRowId cursorCount=${cursor.count}")
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
                    } catch (e: Exception) {
                        Log.w(TAG, "extractParts part processing failed rowId=$mmsRowId error=${e.message}")
                        continue
                    }
                }
            }
        } ?: emptyList<MmsAttachment>().also {
            Log.w(TAG, "extractParts rowId=$mmsRowId returned null cursor")
        }
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

    private fun resolveMmsRow(
        context: Context,
        messageId: String?,
        transactionId: String,
        receivedAtEpochMs: Long,
        senderAddress: String?,
    ): MmsRow? {
        for (token in normalizeTokenCandidates(messageId)) {
            queryMmsRow(context, "$COLUMN_MESSAGE_ID = ?", arrayOf(token))?.let {
                Log.d(TAG, "resolveMmsRow matched m_id token=$token rowId=${it.id}")
                return it
            }
        }

        for (token in normalizeTokenCandidates(transactionId)) {
            queryMmsRow(context, "$COLUMN_TRANSACTION_ID = ?", arrayOf(token))?.let {
                Log.d(TAG, "resolveMmsRow matched tr_id exact token=$token rowId=${it.id}")
                return it
            }
        }

        for (token in normalizeTokenCandidates(transactionId)) {
            queryMmsRow(context, "$COLUMN_TRANSACTION_ID LIKE ?", arrayOf("%$token%"))?.let {
                Log.d(TAG, "resolveMmsRow matched tr_id like token=$token rowId=${it.id}")
                return it
            }
        }

        queryRecentInboxMmsRow(context, receivedAtEpochMs, senderAddress)?.let {
            Log.d(TAG, "resolveMmsRow matched recent inbox rowId=${it.id}")
            return it
        }

        return null
    }

    private fun queryMmsRow(context: Context, selection: String, selectionArgs: Array<String>): MmsRow? {
        return try {
            context.contentResolver.query(
                mmsContentUri,
                rowProjection,
                selection,
                selectionArgs,
                "date DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.toMmsRow()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryMmsRow failed selection=$selection error=${e.message}")
            null
        }
    }

    private fun queryRecentInboxMmsRow(
        context: Context,
        receivedAtEpochMs: Long,
        senderAddress: String?,
        excludeRowId: Long? = null,
    ): MmsRow? {
        val fallbackThresholdSeconds = ((receivedAtEpochMs / 1000L) - RECENT_WINDOW_SECONDS).coerceAtLeast(0)

        return try {
            context.contentResolver.query(
                mmsContentUri,
                rowProjection,
                "$COLUMN_MESSAGE_BOX = ? AND $COLUMN_DATE >= ?",
                arrayOf(MESSAGE_BOX_INBOX.toString(), fallbackThresholdSeconds.toString()),
                "$COLUMN_DATE DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return null
                }

                do {
                    val row = cursor.toMmsRow()
                    if (row.id <= 0) {
                        continue
                    }

                    if (excludeRowId != null && row.id == excludeRowId) {
                        continue
                    }

                    if (senderAddress != null) {
                        val rowSender = resolveSenderAddress(context, row.id)
                        if (!addressesMatch(normalizeAddress(rowSender), senderAddress)) {
                            continue
                        }
                    }

                    if (hasAttachmentPart(context, row.id)) {
                        return row
                    }
                } while (cursor.moveToNext())

                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryRecentInboxMmsRow failed error=${e.message}")
            null
        }
    }

    private fun hasAttachmentPart(context: Context, mmsRowId: Long): Boolean {
        return try {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsRowId/part"),
                arrayOf(COLUMN_ID, COLUMN_MIME_TYPE),
                null,
                null,
                "seq ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (isAttachmentMimeType(cursor.getString(COLUMN_MIME_TYPE))) {
                        return true
                    }
                }
                false
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "hasAttachmentPart failed rowId=$mmsRowId error=${e.message}")
            false
        }
    }

    private fun resolveSenderAddress(context: Context, mmsRowId: Long): String? {
        val uri = Uri.parse("content://mms/$mmsRowId/addr")
        return try {
            context.contentResolver.query(
                uri,
                arrayOf("address", "type"),
                "type = ?",
                arrayOf(ADDRESS_TYPE_FROM.toString()),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString("address")
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeAddress(raw: String?): String? {
        val trimmed = raw?.substringBefore('/')?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return null
        }
        return trimmed.filterNot { it == ' ' || it == '-' }
    }

    private fun addressesMatch(rowAddress: String?, expectedAddress: String?): Boolean {
        if (expectedAddress == null) {
            return true
        }

        if (rowAddress == null) {
            return false
        }

        if (rowAddress == expectedAddress) {
            return true
        }

        val rowDigits = rowAddress.filter { it.isDigit() }
        val expectedDigits = expectedAddress.filter { it.isDigit() }
        if (rowDigits.isNotBlank() && expectedDigits.isNotBlank()) {
            return rowDigits.endsWith(expectedDigits) || expectedDigits.endsWith(rowDigits)
        }

        return false
    }

    private fun pickFileName(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun Cursor.toMmsRow(): MmsRow {
        return MmsRow(
            id = getLong(COLUMN_ID) ?: 0L,
            messageId = getString(COLUMN_MESSAGE_ID),
            transactionId = getString(COLUMN_TRANSACTION_ID),
            subject = getString(COLUMN_SUBJECT),
            contentClass = getString(COLUMN_CONTENT_CLASS),
            size = getLong(COLUMN_MESSAGE_SIZE),
            dateSeconds = getLong(COLUMN_DATE),
        )
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

    private fun Cursor.getLong(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        if (index < 0 || isNull(index)) {
            return null
        }

        return getLong(index)
    }

    private fun Cursor.countSafe(): Int = runCatching { count }.getOrDefault(-1)

    private val rowProjection = arrayOf(
        COLUMN_ID,
        COLUMN_MESSAGE_ID,
        COLUMN_TRANSACTION_ID,
        COLUMN_SUBJECT,
        COLUMN_CONTENT_CLASS,
        COLUMN_MESSAGE_SIZE,
        COLUMN_DATE,
        COLUMN_MESSAGE_BOX,
    )

    private data class MmsRow(
        val id: Long,
        val messageId: String?,
        val transactionId: String?,
        val subject: String?,
        val contentClass: String?,
        val size: Long?,
        val dateSeconds: Long?,
    )

    private const val TAG = "MmsAttachmentExtractor"
}
