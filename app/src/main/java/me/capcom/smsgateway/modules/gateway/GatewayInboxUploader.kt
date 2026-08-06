package me.capcom.smsgateway.modules.gateway

import io.ktor.client.plugins.ClientRequestException
import me.capcom.smsgateway.modules.encryption.AttachmentInput
import me.capcom.smsgateway.modules.encryption.IncomingMessageEncryptor
import me.capcom.smsgateway.modules.encryption.UploadEncryptorScope
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import java.io.File
import java.util.Date

/** Hard cap applied to one MMS part BEFORE base64/encryption (10 MiB). */
const val MAX_INBOX_PART_BYTES = 10L * 1024 * 1024

/**
 * Optional period/type filter for one A4 upload run (event-driven export).
 * Null values mean "no restriction".
 */
data class InboxUploadFilter(
    val since: Long? = null,
    val until: Long? = null,
    val types: Set<IncomingMessageType>? = null,
)

/** Abstracts provider re-read so the flow is unit-testable without Android. */
fun interface IncomingMessageReader {
    fun read(message: IncomingMessage): SmsProviderReader.ReadResult?
}

/** Abstracts MmsAttachmentStorage blob lookup (null = blob gone). */
fun interface StoredAttachmentReader {
    fun find(messageId: String, partId: Long): File?
}

enum class GatewayInboxUploadOutcome {
    /** All pending rows uploaded (or none pending). */
    ALL_UPLOADED,

    /** Passphrase is null -> never upload plaintext. */
    MISSING_PASSPHRASE,

    /** Device has no registration token. */
    MISSING_TOKEN,

    /** A chunk failed with a retryable/network error. */
    RETRY_REQUIRED,

    /** A chunk failed permanently (HTTP 4xx incl. 400/413); rows stay pending. */
    PERMANENT_FAILURE,
}

/**
 * Encrypts and uploads the pending inbox rows to the cloud gateway
 * (plan A-A4). Pure Kotlin - every Android/network touch point is injected.
 *
 * Flow, per run:
 *  1. Read pending rows (all or period/type filtered).
 *  2. No passphrase -> WARN + [GatewayInboxUploadOutcome.MISSING_PASSPHRASE]
 *     (plaintext is NEVER uploaded).
 *  3. Per row: re-read provider content via [reader] (unresolvable -> WARN +
 *     skip), resolve each attachment (stored blob wins, provider re-read is
 *     the fallback; both gone -> WARN + skip) and size-check (cap
 *     [MAX_INBOX_PART_BYTES] checked on the blob's file length BEFORE any
 *     read, and on provider bytes BEFORE base64/encryption - oversize -> WARN +
 *     skip; the message still uploads without that part).
 *  4. One [IncomingMessageEncryptor.openScope] for the WHOLE run -> a single
 *     key derivation at finish() covers every field/attachment.
 *  5. Build [InboxUploadItem]s (isEncrypted=true, id = row id), chunk with the
 *     frozen A3 helper [chunkInboxUpload], upload chunk-by-chunk.
 *  6. Mark uploaded rows PER-CHUNK, PER-ITEM: every id of a successfully
 *     uploaded chunk is [IncomingMessagesRepository.updateUploadedAt]'d right
 *     after that chunk's 2xx. A failing chunk never marks its own ids, so a
 *     partial run stays granular - earlier chunks are NOT re-uploaded on retry.
 *
 * Error handling (requirement A-A4):
 * - HTTP 5xx / network errors -> [GatewayInboxUploadOutcome.RETRY_REQUIRED] ->
 *   the worker returns Result.retry() with exponential backoff.
 * - HTTP 4xx (400/413/401/422...) -> [GatewayInboxUploadOutcome.PERMANENT_FAILURE]
 *   -> the worker returns Result.success() (NO retry, NO infinite loop); the
 *   ids stay pending and WILL be re-attempted on the next save/export trigger.
 *   Documented decision: retrying a dead request only burns battery; leaving
 *   the rows pending keeps them visible and gives future runs a chance.
 */
class GatewayInboxUploader(
    private val passphrase: () -> String?,
    private val deviceToken: () -> String?,
    private val reader: IncomingMessageReader,
    private val storedAttachments: StoredAttachmentReader,
    private val repository: IncomingMessagesRepository,
    private val encryptor: IncomingMessageEncryptor,
    private val logsService: LogsService,
    private val uploadChunk: suspend (token: String, chunk: List<InboxUploadItem>) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    /** Injectable failure classifier; default = real HTTP 4xx detection. */
    private val classifyFailure: (Throwable) -> Boolean = { isPermanentError(it) },
) {

    private data class UploadAttachment(
        val partId: Long,
        val contentType: String,
        val name: String?,
        val size: Long,
        val input: AttachmentInput,
    )

    private data class PlannedRow(
        val message: IncomingMessage,
        val attachments: List<UploadAttachment>,
    )

    suspend fun upload(filter: InboxUploadFilter? = null): GatewayInboxUploadOutcome {
        val pending = repository.selectForUpload(filter?.types, filter?.since, filter?.until)
        if (pending.isEmpty()) return GatewayInboxUploadOutcome.ALL_UPLOADED

        val token = deviceToken()
        if (token == null) {
            warn("Inbox upload skipped: device not registered (no token)")
            return GatewayInboxUploadOutcome.MISSING_TOKEN
        }

        val passphraseValue = passphrase()
        if (passphraseValue == null) {
            warn(IncomingMessageEncryptor.MISSING_PASSPHRASE_MESSAGE)
            return GatewayInboxUploadOutcome.MISSING_PASSPHRASE
        }

        val scope = encryptor.openScope(passphraseValue)
            ?: return GatewayInboxUploadOutcome.MISSING_PASSPHRASE

        val plannedRows = pending.mapNotNull { row -> planRow(row, scope) }
        val encrypted = scope.finish()
        if (plannedRows.isEmpty() || plannedRows.size != encrypted.size) {
            return GatewayInboxUploadOutcome.ALL_UPLOADED
        }

        val items = plannedRows.zip(encrypted).map { (planned, enc) ->
            InboxUploadItem(
                id = planned.message.id,
                type = planned.message.type.wireName(),
                sender = enc.sender,
                recipient = enc.recipient,
                simNumber = planned.message.simNumber,
                content = enc.contentPreview,
                isEncrypted = true,
                createdAt = Date(planned.message.createdAt),
                attachments = enc.attachments.zip(planned.attachments).map { (encAtt, orig) ->
                    InboxUploadAttachment(
                        partId = orig.partId,
                        contentType = orig.contentType,
                        name = orig.name ?: "attachment",
                        size = orig.size,
                        data = encAtt.data,
                    )
                }.takeIf { it.isNotEmpty() },
            )
        }

        // A3 frozen helper: chunks <= INBOX_UPLOAD_BATCH_SIZE (500), one HTTP request per chunk.
        for (chunk in chunkInboxUpload(items, INBOX_UPLOAD_BATCH_SIZE)) {
            try {
                uploadChunk(token, chunk)
            } catch (e: Throwable) {
                return when {
                    classifyFailure(e) -> {
                        warn(
                            "Inbox upload permanently rejected (${errorStatus(e) ?: e.message}); " +
                                "${chunk.size} row(s) stay pending (no retry)."
                        )
                        GatewayInboxUploadOutcome.PERMANENT_FAILURE
                    }

                    else -> GatewayInboxUploadOutcome.RETRY_REQUIRED
                }
            }

            // Granular per-item marking: only the ids of the SUCCESSFUL chunk.
            val uploadedAt = now()
            chunk.forEach { uploaded -> repository.updateUploadedAt(uploaded.id, uploadedAt) }
        }
        return GatewayInboxUploadOutcome.ALL_UPLOADED
    }

    /**
     * Re-reads one row's provider content and resolves its attachments.
     * Returns null when the row is unresolvable (WARN + skip) - never throws.
     */
    private fun planRow(row: IncomingMessage, scope: UploadEncryptorScope): PlannedRow? {
        val provider = reader.read(row)
        if (provider == null) {
            warn("Inbox upload skipped: cannot re-read provider content for ${row.id}")
            return null
        }

        val attachments = mutableListOf<UploadAttachment>()
        provider.parts.forEach { part ->
            val blob = storedAttachments.find(row.id, part.partId)
            // File.length() is a cheap stat: enforce the cap BEFORE readBytes(),
            // which would otherwise allocate the whole blob into heap.
            val bytes: ByteArray? = if (blob != null) {
                if (blob.length() > MAX_INBOX_PART_BYTES) {
                    warn(
                        "Inbox upload skipped part ${part.partId} of ${row.id}: " +
                            "${blob.length()} bytes exceeds the ${MAX_INBOX_PART_BYTES} cap"
                    )
                    return@forEach
                }
                blob.readBytes()
            } else {
                part.data
            }

            if (bytes == null) {
                if (part.size != null && part.size > MAX_INBOX_PART_BYTES) {
                    warn(
                        "Inbox upload skipped part ${part.partId} of ${row.id}: " +
                            "${part.size} bytes exceeds the ${MAX_INBOX_PART_BYTES} cap"
                    )
                } else {
                    warn("Inbox upload skipped part ${part.partId} of ${row.id}: no data (blob and provider both gone)")
                }
                return@forEach
            }
            if (bytes.size > MAX_INBOX_PART_BYTES) {
                warn(
                    "Inbox upload skipped part ${part.partId} of ${row.id}: " +
                        "${bytes.size} bytes exceeds the ${MAX_INBOX_PART_BYTES} cap"
                )
                return@forEach
            }

            attachments += UploadAttachment(
                partId = part.partId,
                contentType = part.contentType,
                name = part.name,
                size = bytes.size.toLong(),
                input = AttachmentInput(name = part.name ?: "attachment", data = bytes),
            )
        }

        scope.addMessage(row.sender, row.recipient, provider.content, attachments.map { it.input })
        return PlannedRow(message = row, attachments = attachments)
    }

    private fun warn(message: String) {
        logsService.insert(LogEntry.Priority.WARN, MODULE_NAME, message)
    }

    ///////////////////////////////////////////////////////////////////////////

    companion object {
        /**
         * Permanent (never retried) upload failures: any HTTP 4xx from the
         * gateway (400 bad payload, 401/403 auth, 404, 413 payload too large,
         * 422). 5xx and transport errors stay retryable.
         */
        internal fun isPermanentError(e: Throwable): Boolean =
            errorStatus(e)?.let { it >= 400 && it < 500 } == true

        internal fun errorStatus(e: Throwable): Int? = when (e) {
            is ClientRequestException -> e.response.status.value
            else -> (e.cause as? ClientRequestException)?.response?.status?.value
        }
    }
}

/** Maps the Room enum to the frozen wire type constants (A3 contract). */
internal fun IncomingMessageType.wireName(): String = when (this) {
    IncomingMessageType.SMS -> InboxMessageType.SMS
    IncomingMessageType.DATA_SMS -> InboxMessageType.DATA_SMS
    IncomingMessageType.MMS -> InboxMessageType.MMS
    IncomingMessageType.MMS_DOWNLOADED -> InboxMessageType.MMS_DOWNLOADED
}
