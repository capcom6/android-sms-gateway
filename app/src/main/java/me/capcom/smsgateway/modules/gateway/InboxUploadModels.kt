package me.capcom.smsgateway.modules.gateway

import java.util.Date

/**
 * Wire contract for device inbox upload, mirroring client-go
 * requests_mobile.go MobilePostInboxRequest byte-for-byte.
 *
 * - nullable fields are omitted when null (Go omitempty)
 * - `attachments` is omitted when null/empty (Go omitempty, len == 0)
 * - `isEncrypted` is always `true` from the device
 * - `createdAt` serializes as RFC3339 with timezone offset via the shared
 *   Gson Date format (extensions/GsonBuilder.kt, `setDateFormatISO8601`)
 * - attachment `data` is a base64 STRING of raw bytes (Go []byte)
 */
internal const val INBOX_UPLOAD_BATCH_SIZE = 500

object InboxMessageType {
    const val SMS = "SMS"
    const val DATA_SMS = "DATA_SMS"
    const val MMS = "MMS"
    const val MMS_DOWNLOADED = "MMS_DOWNLOADED"
}

data class InboxUploadItem(
    val id: String,
    val type: String,
    val sender: String,
    val recipient: String? = null,
    val simNumber: Int? = null,
    val content: String,
    val isEncrypted: Boolean = true,
    val createdAt: Date,
    val attachments: List<InboxUploadAttachment>? = null,
)

data class InboxUploadAttachment(
    val partId: Long,
    val contentType: String,
    val name: String,
    val size: Long? = null,
    val data: String, // base64 of raw bytes
)

/**
 * Normalizes an upload batch to the exact frozen wire shape:
 * - null/empty attachments become null so Gson omits the field
 *   (Go `omitempty` omits both nil and len==0)
 * - isEncrypted is ALWAYS true (server rejects `false`)
 */
internal fun List<InboxUploadItem>.prepareInboxUpload(): List<InboxUploadItem> =
    map { item ->
        item.copy(
            attachments = item.attachments?.takeIf { it.isNotEmpty() },
            isEncrypted = true,
        )
    }

/**
 * Splits the batch into HTTP-safe chunks (single request must not exceed
 * [INBOX_UPLOAD_BATCH_SIZE] items). Pure helper, independently testable.
 */
internal fun chunkInboxUpload(
    items: List<InboxUploadItem>,
    batchSize: Int = INBOX_UPLOAD_BATCH_SIZE,
): List<List<InboxUploadItem>> = items.chunked(batchSize)